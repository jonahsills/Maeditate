import { S3Client, PutObjectCommand, GetObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import dotenv from 'dotenv';

dotenv.config();

const s3Client = new S3Client({
  region: process.env.S3_REGION,
  credentials: {
    accessKeyId: process.env.S3_ACCESS_KEY_ID!,
    secretAccessKey: process.env.S3_SECRET_ACCESS_KEY!
  }
});

export class S3Service {
  private bucket: string;
  private cdnBase: string;

  constructor() {
    this.bucket = process.env.S3_BUCKET!;
    this.cdnBase = process.env.PUBLIC_CDN_BASE!;
  }

  /**
   * Generate a presigned URL for uploading audio files
   */
  async generateUploadUrl(
    fileKey: string, 
    contentType: string, 
    metadata: Record<string, string> = {}
  ): Promise<string> {
    const command = new PutObjectCommand({
      Bucket: this.bucket,
      Key: fileKey,
      ContentType: contentType,
      Metadata: metadata,
      // Set cache control for audio files
      CacheControl: 'public, max-age=31536000', // 1 year
    });

    return await getSignedUrl(s3Client, command, { expiresIn: 3600 }); // 1 hour
  }

  /**
   * Generate a presigned URL for downloading audio files
   */
  async generateDownloadUrl(fileKey: string): Promise<string> {
    const command = new GetObjectCommand({
      Bucket: this.bucket,
      Key: fileKey,
    });

    return await getSignedUrl(s3Client, command, { expiresIn: 3600 }); // 1 hour
  }

  /**
   * Generate the public CDN URL for an audio file
   */
  generatePublicUrl(fileKey: string): string {
    return `${this.cdnBase}/${fileKey}`;
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
   * Download audio file from S3 for processing
   */
  async downloadAudio(fileKey: string): Promise<Buffer> {
    const command = new GetObjectCommand({
      Bucket: this.bucket,
      Key: fileKey,
    });

    const response = await s3Client.send(command);
    
    if (!response.Body) {
      throw new Error('No audio data found');
    }

    // Convert stream to buffer
    const chunks: Uint8Array[] = [];
    const stream = response.Body as any;
    
    for await (const chunk of stream) {
      chunks.push(chunk);
    }

    return Buffer.concat(chunks);
  }

  /**
   * Validate file extension for audio files
   */
  isValidAudioExtension(ext: string): boolean {
    const validExtensions = ['mp3', 'wav', 'm4a', 'aac', 'ogg', 'flac'];
    return validExtensions.includes(ext.toLowerCase());
  }

  /**
   * Get file size from S3 metadata
   */
  async getFileSize(fileKey: string): Promise<number> {
    const command = new GetObjectCommand({
      Bucket: this.bucket,
      Key: fileKey,
    });

    const response = await s3Client.send(command);
    return response.ContentLength || 0;
  }
}

export const s3Service = new S3Service();
