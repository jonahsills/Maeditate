import fs from 'fs';
import path from 'path';
import { v4 as uuidv4 } from 'uuid';

export class LocalStorageService {
  private uploadDir: string;

  constructor() {
    this.uploadDir = path.join(process.cwd(), 'uploads');
    this.ensureUploadDir();
  }

  private ensureUploadDir() {
    if (!fs.existsSync(this.uploadDir)) {
      fs.mkdirSync(this.uploadDir, { recursive: true });
    }
  }

  /**
   * Generate a unique file key for audio uploads
   */
  generateFileKey(sessionId: string, fileExt: string): string {
    const timestamp = Date.now();
    const randomId = Math.random().toString(36).substring(2, 8);
    return `audio/${sessionId}/${timestamp}-${randomId}.${fileExt}`;
  }

  /**
   * Generate the public URL for an audio file
   */
  generatePublicUrl(fileKey: string): string {
    return `${process.env.BACKEND_BASE_URL || 'http://localhost:3000'}/uploads/${fileKey}`;
  }

  /**
   * Save uploaded file to local storage
   */
  async saveFile(fileKey: string, buffer: Buffer): Promise<void> {
    const filePath = path.join(this.uploadDir, fileKey);
    const dir = path.dirname(filePath);
    
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    
    fs.writeFileSync(filePath, buffer);
  }

  /**
   * Get file from local storage
   */
  async getFile(fileKey: string): Promise<Buffer> {
    const filePath = path.join(this.uploadDir, fileKey);
    return fs.readFileSync(filePath);
  }

  /**
   * Validate file extension for audio files
   */
  isValidAudioExtension(ext: string): boolean {
    const validExtensions = ['mp3', 'wav', 'm4a', 'aac', 'ogg', 'flac'];
    return validExtensions.includes(ext.toLowerCase());
  }

  /**
   * Get file size
   */
  async getFileSize(fileKey: string): Promise<number> {
    const filePath = path.join(this.uploadDir, fileKey);
    const stats = fs.statSync(filePath);
    return stats.size;
  }
}

export const localStorageService = new LocalStorageService();
