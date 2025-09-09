import { z } from 'zod';

// Database types
export interface User {
  id: string;
  email?: string;
  created_at: Date;
  updated_at: Date;
}

export interface Device {
  id: string;
  user_id: string;
  model: string;
  created_at: Date;
  updated_at: Date;
}

export interface Session {
  id: string;
  user_id: string;
  device_id: string;
  started_at: Date;
  duration_sec?: number;
  created_at: Date;
  updated_at: Date;
}

export type TranscriptStatus = 'PENDING' | 'TRANSCRIBING' | 'SUMMARIZING' | 'COMPLETE' | 'FAILED';

export interface Transcript {
  id: string;
  session_id: string;
  idem_key: string;
  audio_url?: string;
  text?: string;
  language: string;
  confidence?: number;
  status: TranscriptStatus;
  created_at: Date;
  updated_at: Date;
  error?: string;
}

export interface Summary {
  id: string;
  session_id: string;
  model: string;
  text: string;
  created_at: Date;
}

// API Request/Response types
export const AuthRequestSchema = z.object({
  deviceModel: z.string().min(1).max(100)
});

export const UploadInitSchema = z.object({
  fileExt: z.string().regex(/^[a-zA-Z0-9]+$/),
  contentType: z.string().startsWith('audio/'),
  sessionId: z.string().uuid().optional()
});

export const TranscriptSchema = z.object({
  sessionId: z.string().uuid().optional(),
  audioUrl: z.string().url().optional(),
  text: z.string().optional(),
  language: z.string().default('en'),
  confidence: z.number().min(0).max(1).optional(),
  wantSummary: z.boolean().default(false),
  meta: z.object({
    durationSec: z.number().optional(),
    device: z.string().optional()
  }).optional()
}).refine(data => data.audioUrl || data.text, {
  message: "Either audioUrl or text must be provided"
});

// JWT payload
export interface JWTPayload {
  sub: string; // user_id
  deviceId: string;
  iat?: number;
  exp?: number;
}

// STT Provider response
export interface STTResult {
  text: string;
  language: string;
  confidence: number;
}

// Gemini response
export interface GeminiResponse {
  text: string;
  model: string;
}

// API Response types
export interface AuthResponse {
  token: string;
  userId: string;
  deviceId: string;
  expiresInSec: number;
}

export interface UploadInitResponse {
  sessionId: string;
  uploadUrl: string;
  audioUrl: string;
}

export interface TranscriptResponse {
  ok: boolean;
  sessionId: string;
  transcriptId: string;
  status: TranscriptStatus;
}

export interface TranscriptStatusResponse {
  id: string;
  status: TranscriptStatus;
  sessionId: string;
  text?: string;
  language?: string;
  confidence?: number;
  summary?: {
    id: string;
    model: string;
    text: string;
  };
  error?: string;
}

export interface SessionsListResponse {
  sessions: Array<Session & {
    transcript_count: number;
    last_activity: Date;
  }>;
  nextCursor?: string;
}

export interface SessionDetailResponse {
  session: Session;
  transcripts: Array<Transcript & {
    summary_text?: string;
    summary_model?: string;
  }>;
}
