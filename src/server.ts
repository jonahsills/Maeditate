import express, { Request, Response, NextFunction, RequestHandler } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';
import multer from 'multer';
import path from 'path';

import pool from './config/database';
// import { runMigrations } from '../scripts/migrate';
import { localStorageService } from './services/localStorage';
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

// Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN?.split(','),
  credentials: true
}));
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));

// Configure multer for file uploads
const upload = multer({ 
  storage: multer.memoryStorage(),
  limits: { fileSize: 50 * 1024 * 1024 } // 50MB limit
});

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

// Serve uploaded files
app.use('/uploads', express.static(path.join(process.cwd(), 'uploads')));

// Auth middleware
const authenticateToken: RequestHandler = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  jwt.verify(token, process.env.JWT_SECRET!, (err: any, user: any) => {
    if (err) {
      return res.status(403).json({ error: 'Invalid or expired token' });
    }
    req.user = { userId: String(user.sub), deviceId: user.deviceId };
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
app.post('/v1/upload-init', authenticateToken, (async (req: Request, res: Response) => {
  try {
    const { fileExt, contentType, sessionId } = UploadInitSchema.parse(req.body);
    
    // Validate file extension
    if (!localStorageService.isValidAudioExtension(fileExt)) {
      return res.status(400).json({ error: 'Invalid audio file extension' });
    }
    
    let sessionIdToUse: string;
    if (!req.user) {
      res.status(401).json({ error: 'unauthorized' });
      return;
    }
    const user = req.user;

    if (sessionId) {
      sessionIdToUse = sessionId;
    } else {
      // Create session if not provided
      const sessionResult = await pool.query(
        'INSERT INTO sessions (user_id, device_id) VALUES ($1, $2) RETURNING id',
        [user.userId, user.deviceId]
      );
      sessionIdToUse = sessionResult.rows[0].id;
    }
    
    // Generate unique file key
    const fileKey = localStorageService.generateFileKey(sessionIdToUse, fileExt);
    const audioUrl = localStorageService.generatePublicUrl(fileKey);
    
    // For local storage, we'll use a simple upload endpoint
    const uploadUrl = `${process.env.BACKEND_BASE_URL || 'http://localhost:3000'}/v1/upload/${fileKey}`;
    
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
}) as RequestHandler);

// File upload endpoint
app.post('/v1/upload/:fileKey', upload.single('audio'), async (req: Request, res: Response) => {
  try {
    const { fileKey } = req.params;
    const file = req.file;
    
    if (!file) {
      return res.status(400).json({ error: 'No file uploaded' });
    }
    
    // Save file to local storage
    await localStorageService.saveFile(fileKey, file.buffer);
    
    res.json({ success: true, fileKey });
  } catch (error: any) {
    console.error('Upload error:', error);
    res.status(500).json({ error: 'Upload failed' });
  }
});

// 4.3 Create transcript - Submit audio for processing
app.post('/v1/transcripts', authenticateToken, transcriptLimiter, (async (req: Request, res: Response) => {
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
    if (!req.user) {
      res.status(401).json({ error: 'unauthorized' });
      return;
    }
    const user = req.user;

    if (data.sessionId) {
      sessionIdToUse = data.sessionId;
    } else {
      // Create session if not provided
      const sessionResult = await pool.query(
        'INSERT INTO sessions (user_id, device_id) VALUES ($1, $2) RETURNING id',
        [user.userId, user.deviceId]
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
}) as RequestHandler);

// 4.4 Get transcript status - Check processing status
app.get('/v1/transcripts/:id', authenticateToken, (async (req: Request, res: Response) => {
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
}) as RequestHandler);

// 4.5 List sessions - Get user's sessions
app.get('/v1/sessions', authenticateToken, (async (req: Request, res: Response) => {
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
    
    if (!req.user) {
      res.status(401).json({ error: 'unauthorized' });
      return;
    }
    const user = req.user;
    const params: any[] = [user.userId];
    
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
}) as RequestHandler);

// 4.6 Get session detail - Get session with transcripts
app.get('/v1/sessions/:id', authenticateToken, (async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const sessionResult = await pool.query(
      'SELECT * FROM sessions WHERE id = $1 AND user_id = $2',
      [id, (req.user as Express.UserPayload).userId]
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
}) as RequestHandler);

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
    const backendBaseUrl = process.env.BACKEND_BASE_URL || 'http://localhost:3000';
    const fileKey = audioUrl.replace(backendBaseUrl + '/uploads/', '');
    
    // Get audio from local storage
    const audioBuffer = await localStorageService.getFile(fileKey);
    
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
app.listen(PORT, async () => {
  console.log(`Voice Recorder Backend running on port ${PORT}`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
  
  // Run database migrations on startup
  try {
    console.log('Running database migrations...');
    const fs = require('fs');
    const migrationPath = require('path').join(__dirname, '../migrations/001_initial.sql');
    const migrationSQL = fs.readFileSync(migrationPath, 'utf8');
    await pool.query(migrationSQL);
    console.log('✅ Database migrations completed');
  } catch (error) {
    console.error('❌ Database migration failed:', error);
  }
});

export default app;
