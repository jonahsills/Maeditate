import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';

import pool from './config/database';
import { s3Service } from './services/s3';
import { aiService } from './services/ai';
import { 
  AuthRequestSchema, 
  UploadInitSchema, 
  TranscriptSchema,
  JWTPayload,
  AuthResponse,
  UploadInitResponse,
  TranscriptResponse,
  TranscriptStatusResponse,
  SessionsListResponse,
  SessionDetailResponse
} from './types';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Extend Express Request with authenticated user
interface AuthenticatedRequest extends Request {
  user: JWTPayload;
}

// Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN?.split(','),
  credentials: true
}));
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));

// Rate limiting
const generalLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000'), // 15 minutes
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '120'),
  keyGenerator: (req) => (req.ip || 'unknown'),
  message: { error: 'Too many requests, please try again later.' }
});

const transcriptLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || '900000'),
  max: parseInt(process.env.TRANSCRIPT_RATE_LIMIT_MAX || '10'),
  keyGenerator: (req) => (req.ip || 'unknown'),
  message: { error: 'Too many transcription requests, please slow down.' }
});

app.use(generalLimiter);

// Auth middleware
const authenticateToken = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  jwt.verify(token, process.env.JWT_SECRET!, (err: any, user: any) => {
    if (err) {
      return res.status(403).json({ error: 'Invalid or expired token' });
    }
    req.user = user;
    next();
  });
};

// Health check
app.get('/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    timestamp: new Date().toISOString(),
    version: '1.0.0'
  });
});

// 4.1 Auth - Anonymous user creation
app.post('/auth/anonymous', async (req, res) => {
  try {
    const { deviceModel } = AuthRequestSchema.parse(req.body);
    
    // Create or get user
    const userResult = await pool.query(
      'INSERT INTO users (id) VALUES (uuid_generate_v4()) RETURNING id'
    );
    const userId = userResult.rows[0].id;
    
    // Create device
    const deviceResult = await pool.query(
      'INSERT INTO devices (user_id, model) VALUES ($1, $2) RETURNING id',
      [userId, deviceModel]
    );
    const deviceId = deviceResult.rows[0].id;
    
    // Generate JWT
    const token = jwt.sign(
      { sub: userId, deviceId },
      process.env.JWT_SECRET!,
      { expiresIn: '7d' }
    );
    
    const response: AuthResponse = {
      token,
      userId,
      deviceId,
      expiresInSec: 604800
    };
    
    res.json(response);
  } catch (error: any) {
    console.error('Auth error:', error);
    res.status(400).json({ error: 'Invalid request' });
  }
});

// 4.2 Upload init - Get presigned URL for audio upload
app.post('/v1/upload-init', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const { fileExt, contentType, sessionId } = UploadInitSchema.parse(req.body);
    
    // Validate file extension
    if (!s3Service.isValidAudioExtension(fileExt)) {
      return res.status(400).json({ error: 'Invalid audio file extension' });
    }
    
    let sessionIdToUse: string;
    if (sessionId) {
      sessionIdToUse = sessionId;
    } else {
      // Create session if not provided
      const sessionResult = await pool.query(
        'INSERT INTO sessions (user_id, device_id) VALUES ($1, $2) RETURNING id',
        [req.user.sub, req.user.deviceId]
      );
      sessionIdToUse = sessionResult.rows[0].id;
    }
    
    // Generate unique file key
    const fileKey = s3Service.generateFileKey(sessionIdToUse, fileExt);
    const audioUrl = s3Service.generatePublicUrl(fileKey);
    
    // Generate presigned URL
    const uploadUrl = await s3Service.generateUploadUrl(
      fileKey, 
      contentType,
      {
        sessionId: sessionIdToUse,
        userId: req.user.sub,
        deviceId: req.user.deviceId
      }
    );
    
    const response: UploadInitResponse = {
      sessionId: sessionIdToUse,
      uploadUrl,
      audioUrl
    };
    
    res.json(response);
  } catch (error: any) {
    console.error('Upload init error:', error);
    res.status(400).json({ error: 'Invalid request' });
  }
});

// 4.3 Create transcript - Submit audio for processing
app.post('/v1/transcripts', authenticateToken, transcriptLimiter, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const idemKey = req.headers['idempotency-key'] as string;
    if (!idemKey) {
      return res.status(400).json({ error: 'Idempotency-Key header required' });
    }
    
    const data = TranscriptSchema.parse(req.body);
    
    // Check for existing transcript with same idem key
    const existing = await pool.query(
      'SELECT * FROM transcripts WHERE idem_key = $1',
      [idemKey]
    );
    
    if (existing.rows.length > 0) {
      const existingTranscript = existing.rows[0];
      const response: TranscriptResponse = {
        ok: true,
        sessionId: existingTranscript.session_id,
        transcriptId: existingTranscript.id,
        status: existingTranscript.status
      };
      return res.json(response);
    }
    
    let sessionIdToUse: string;
    if (data.sessionId) {
      sessionIdToUse = data.sessionId;
    } else {
      // Create session if not provided
      const sessionResult = await pool.query(
        'INSERT INTO sessions (user_id, device_id) VALUES ($1, $2) RETURNING id',
        [req.user.sub, req.user.deviceId]
      );
      sessionIdToUse = sessionResult.rows[0].id;
    }
    
    // Create transcript record
    const transcriptResult = await pool.query(
      `INSERT INTO transcripts (session_id, idem_key, audio_url, text, language, confidence, status)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
      [
        sessionIdToUse,
        idemKey,
        data.audioUrl || null,
        data.text || null,
        data.language,
        data.confidence || null,
        'PENDING'
      ]
    );
    
    const transcriptId = transcriptResult.rows[0].id;
    
    // Process transcript asynchronously
    if (data.audioUrl) {
      processAudioTranscription(transcriptId, data.audioUrl, data.wantSummary);
    } else if (data.text) {
      processTextTranscription(transcriptId, data.text, data.wantSummary);
    }
    
    const response: TranscriptResponse = {
      ok: true,
      sessionId: sessionIdToUse,
      transcriptId,
      status: 'PENDING'
    };
    
    res.json(response);
  } catch (error: any) {
    console.error('Transcript creation error:', error);
    res.status(400).json({ error: 'Invalid request' });
  }
});

// 4.4 Get transcript status - Check processing status
app.get('/v1/transcripts/:id', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await pool.query(`
      SELECT t.*, s.text as summary_text, s.model as summary_model, s.id as summary_id
      FROM transcripts t
      LEFT JOIN summaries s ON t.session_id = s.session_id
      WHERE t.id = $1
    `, [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Transcript not found' });
    }
    
    const transcript = result.rows[0];
    const response: TranscriptStatusResponse = {
      id: transcript.id,
      status: transcript.status,
      sessionId: transcript.session_id
    };
    
    if (transcript.status === 'COMPLETE') {
      response.text = transcript.text;
      response.language = transcript.language;
      response.confidence = transcript.confidence;
      
      if (transcript.summary_text) {
        response.summary = {
          id: transcript.summary_id,
          model: transcript.summary_model,
          text: transcript.summary_text
        };
      }
    } else if (transcript.status === 'FAILED') {
      response.error = transcript.error;
    }
    
    res.json(response);
  } catch (error: any) {
    console.error('Transcript status error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// 4.5 List sessions - Get user's sessions
app.get('/v1/sessions', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const limit = Math.min(parseInt(req.query.limit as string) || 20, 100);
    const cursor = req.query.cursor as string;
    
    let query = `
      SELECT s.*, 
             COUNT(t.id) as transcript_count,
             MAX(t.created_at) as last_activity
      FROM sessions s
      LEFT JOIN transcripts t ON s.id = t.session_id
      WHERE s.user_id = $1
    `;
    
    const params: any[] = [req.user.sub];
    
    if (cursor) {
      query += ' AND s.created_at < $2';
      params.push(cursor);
    }
    
    query += `
      GROUP BY s.id
      ORDER BY s.created_at DESC
      LIMIT $${params.length + 1}
    `;
    params.push(limit);
    
    const result = await pool.query(query, params);
    
    const response: SessionsListResponse = {
      sessions: result.rows,
      nextCursor: result.rows.length === limit ? 
        result.rows[result.rows.length - 1].created_at : undefined
    };
    
    res.json(response);
  } catch (error: any) {
    console.error('Sessions list error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// 4.6 Get session detail - Get session with transcripts
app.get('/v1/sessions/:id', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  try {
    const { id } = req.params;
    
    const sessionResult = await pool.query(
      'SELECT * FROM sessions WHERE id = $1 AND user_id = $2',
      [id, req.user.sub]
    );
    
    if (sessionResult.rows.length === 0) {
      return res.status(404).json({ error: 'Session not found' });
    }
    
    const transcriptsResult = await pool.query(`
      SELECT t.*, s.text as summary_text, s.model as summary_model
      FROM transcripts t
      LEFT JOIN summaries s ON t.session_id = s.session_id
      WHERE t.session_id = $1
      ORDER BY t.created_at DESC
    `, [id]);
    
    const response: SessionDetailResponse = {
      session: sessionResult.rows[0],
      transcripts: transcriptsResult.rows
    };
    
    res.json(response);
  } catch (error: any) {
    console.error('Session detail error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Background processing functions
async function processAudioTranscription(transcriptId: string, audioUrl: string, wantSummary: boolean) {
  try {
    console.log(`Processing audio transcription for transcript ${transcriptId}`);
    
    // Update status to TRANSCRIBING
    await pool.query(
      'UPDATE transcripts SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      ['TRANSCRIBING', transcriptId]
    );
    
    // Extract file key from audio URL
    const fileKey = audioUrl.replace(process.env.PUBLIC_CDN_BASE + '/', '');
    
    // Download audio from S3
    const audioBuffer = await s3Service.downloadAudio(fileKey);
    
    // Validate audio file
    const maxSizeMB = parseInt(process.env.MAX_FILE_SIZE_MB || '50');
    aiService.validateAudioFile(audioBuffer, maxSizeMB);
    
    // Transcribe audio
    const transcription = await aiService.transcribeAudio(audioBuffer, 'audio.m4a');
    
    // Update with transcription result
    await pool.query(
      'UPDATE transcripts SET text = $1, language = $2, confidence = $3, status = $4, updated_at = CURRENT_TIMESTAMP WHERE id = $5',
      [transcription.text, transcription.language, transcription.confidence, 'SUMMARIZING', transcriptId]
    );
    
    if (wantSummary) {
      // Generate summary
      const summary = await aiService.generateSummary(transcription.text);
      
      // Store summary
      const sessionResult = await pool.query(
        'SELECT session_id FROM transcripts WHERE id = $1',
        [transcriptId]
      );
      const sessionId = sessionResult.rows[0].session_id;
      
      await pool.query(
        'INSERT INTO summaries (session_id, model, text) VALUES ($1, $2, $3)',
        [sessionId, summary.model, summary.text]
      );
    }
    
    // Mark as complete
    await pool.query(
      'UPDATE transcripts SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      ['COMPLETE', transcriptId]
    );
    
    console.log(`Completed audio transcription for transcript ${transcriptId}`);
    
  } catch (error: any) {
    console.error(`Audio transcription failed for transcript ${transcriptId}:`, error);
    
    // Mark as failed
    await pool.query(
      'UPDATE transcripts SET status = $1, error = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $3',
      ['FAILED', error.message, transcriptId]
    );
  }
}

async function processTextTranscription(transcriptId: string, text: string, wantSummary: boolean) {
  try {
    console.log(`Processing text transcription for transcript ${transcriptId}`);
    
    // Update status to SUMMARIZING if summary requested, otherwise COMPLETE
    const status = wantSummary ? 'SUMMARIZING' : 'COMPLETE';
    await pool.query(
      'UPDATE transcripts SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      [status, transcriptId]
    );
    
    if (wantSummary) {
      // Generate summary
      const summary = await aiService.generateSummary(text);
      
      // Store summary
      const sessionResult = await pool.query(
        'SELECT session_id FROM transcripts WHERE id = $1',
        [transcriptId]
      );
      const sessionId = sessionResult.rows[0].session_id;
      
      await pool.query(
        'INSERT INTO summaries (session_id, model, text) VALUES ($1, $2, $3)',
        [sessionId, summary.model, summary.text]
      );
      
      // Mark as complete
      await pool.query(
        'UPDATE transcripts SET status = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
        ['COMPLETE', transcriptId]
      );
    }
    
    console.log(`Completed text transcription for transcript ${transcriptId}`);
    
  } catch (error: any) {
    console.error(`Text transcription failed for transcript ${transcriptId}:`, error);
    
    // Mark as failed
    await pool.query(
      'UPDATE transcripts SET status = $1, error = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $3',
      ['FAILED', error.message, transcriptId]
    );
  }
}

// Error handling middleware
app.use((error: any, req: any, res: any, next: any) => {
  console.error('Unhandled error:', error);
  res.status(500).json({ error: 'Internal server error' });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

// Start server
app.listen(PORT, () => {
  console.log(`Voice Recorder Backend running on port ${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});

export default app;
