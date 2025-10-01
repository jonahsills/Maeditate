import axios from 'axios';
import FormData from 'form-data';
import { STTResult, GeminiResponse } from '../types';
import dotenv from 'dotenv';

dotenv.config();

export class AIService {
  private geminiApiKey: string;
  private openaiApiKey: string;
  private sttProvider: string;

  constructor() {
    this.geminiApiKey = process.env.GEMINI_API_KEY!;
    this.openaiApiKey = process.env.OPENAI_API_KEY!;
    this.sttProvider = process.env.STT_PROVIDER || 'whisper';
  }

  /**
   * Transcribe audio using the configured STT provider
   */
  async transcribeAudio(audioBuffer: Buffer, filename: string): Promise<STTResult> {
    switch (this.sttProvider) {
      case 'whisper':
        return await this.transcribeWithWhisper(audioBuffer, filename);
      default:
        throw new Error(`Unsupported STT provider: ${this.sttProvider}`);
    }
  }

  /**
   * Transcribe audio using OpenAI Whisper API
   */
  private async transcribeWithWhisper(audioBuffer: Buffer, filename: string): Promise<STTResult> {
    try {
      const formData = new FormData();
      formData.append('file', audioBuffer, {
        filename: filename,
        contentType: 'audio/mp4'
      });
      formData.append('model', 'whisper-1');
      formData.append('language', 'en');
      formData.append('response_format', 'json');

      const response = await axios.post(
        'https://api.openai.com/v1/audio/transcriptions',
        formData,
        {
          headers: {
            'Authorization': `Bearer ${this.openaiApiKey}`,
            ...formData.getHeaders()
          },
          timeout: 60000, // 60 seconds
          maxContentLength: 25 * 1024 * 1024, // 25MB
        }
      );

      return {
        text: response.data.text,
        language: 'en',
        confidence: 0.95 // Whisper doesn't provide confidence scores
      };
    } catch (error: any) {
      console.error('Whisper transcription error:', error.response?.data || error.message);
      throw new Error(`Transcription failed: ${error.response?.data?.error?.message || error.message}`);
    }
  }

  /**
   * Generate summary using Gemini API
   */
  async generateSummary(text: string): Promise<GeminiResponse> {
    try {
      const prompt = `You are a concise meditation note summarizer. Summarize the following session in ≤ 80 words with a positive tone.

Text: ${text}`;

      const response = await axios.post(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${this.geminiApiKey}`,
        {
          contents: [{
            parts: [{
              text: prompt
            }]
          }],
          generationConfig: {
            maxOutputTokens: 256,
            temperature: 0.7,
            topP: 0.8,
            topK: 40
          }
        },
        {
          headers: {
            'Content-Type': 'application/json'
          },
          timeout: 30000, // 30 seconds
        }
      );

      const generatedText = response.data?.candidates?.[0]?.content?.parts?.[0]?.text;
      
      if (!generatedText) {
        throw new Error('No summary generated from Gemini');
      }

      return {
        text: generatedText.trim(),
        model: 'gemini-1.5-flash'
      };
    } catch (error: any) {
      console.error('Gemini summarization error:', error.response?.data || error.message);
      throw new Error(`Summarization failed: ${error.response?.data?.error?.message || error.message}`);
    }
  }

  /**
   * Validate audio file before processing
   */
  validateAudioFile(audioBuffer: Buffer, maxSizeMB: number = 50): void {
    const maxSizeBytes = maxSizeMB * 1024 * 1024;
    
    if (audioBuffer.length > maxSizeBytes) {
      throw new Error(`Audio file too large. Maximum size: ${maxSizeMB}MB`);
    }

    if (audioBuffer.length === 0) {
      throw new Error('Audio file is empty');
    }

    // Basic audio file validation (check for common audio file headers)
    const header = audioBuffer.slice(0, 12);
    const isValidAudio = this.isValidAudioHeader(header);
    
    if (!isValidAudio) {
      throw new Error('Invalid audio file format');
    }
  }

  /**
   * Check if buffer contains valid audio file header
   */
  private isValidAudioHeader(header: Buffer): boolean {
    // Check for common audio file signatures
    const signatures = [
      [0xFF, 0xFB], // MP3
      [0xFF, 0xF3], // MP3
      [0xFF, 0xF2], // MP3
      [0x49, 0x44, 0x33], // ID3 (MP3)
      [0x52, 0x49, 0x46, 0x46], // RIFF (WAV)
      [0x4F, 0x67, 0x67, 0x53], // OggS (OGG)
      [0x66, 0x4C, 0x61, 0x43], // fLaC (FLAC)
    ];

    for (const signature of signatures) {
      if (this.bufferStartsWith(header, signature)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if buffer starts with given signature
   */
  private bufferStartsWith(buffer: Buffer, signature: number[]): boolean {
    if (buffer.length < signature.length) {
      return false;
    }

    for (let i = 0; i < signature.length; i++) {
      if (buffer[i] !== signature[i]) {
        return false;
      }
    }

    return true;
  }

  /**
   * Estimate audio duration from file size (rough approximation)
   */
  estimateAudioDuration(fileSizeBytes: number, bitrateKbps: number = 128): number {
    // Rough calculation: file size / (bitrate / 8) / 1000
    return Math.round(fileSizeBytes / (bitrateKbps / 8) / 1000);
  }
}

export const aiService = new AIService();
