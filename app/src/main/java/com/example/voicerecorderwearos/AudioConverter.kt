package com.example.voicerecorderwearos

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioConverter(private val context: Context) {
    private val TAG = "AudioConverter"
    
    /**
     * Converts audio file to Whisper-compatible WAV format using MediaCodec
     * @param inputFile The input audio file
     * @return Converted WAV file or null if conversion failed
     */
    fun convertToWhisperWav(inputFile: File): File? {
        try {
            Log.d(TAG, "=== AUDIO CONVERSION STARTED ===")
            Log.d(TAG, "Input file: ${inputFile.absolutePath}")
            Log.d(TAG, "Input file size: ${inputFile.length()} bytes")
            
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist")
                return null
            }
            
            // Create output file path
            val outputFileName = inputFile.nameWithoutExtension + "_whisper.wav"
            val outputFile = File(context.getExternalFilesDir(null), outputFileName)
            
            Log.d(TAG, "Output file: ${outputFile.absolutePath}")
            
            // Convert using MediaCodec
            val success = convertToWav(inputFile, outputFile)
            
            if (success && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "=== AUDIO CONVERSION SUCCESSFUL ===")
                Log.d(TAG, "Output file size: ${outputFile.length()} bytes")
                Log.d(TAG, "Output file exists: ${outputFile.exists()}")
                return outputFile
            } else {
                Log.e(TAG, "=== AUDIO CONVERSION FAILED ===")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== AUDIO CONVERSION ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Converts audio file to MP3 format as fallback
     * Note: This is a simplified implementation that returns the original file
     * since MediaCodec MP3 encoding is complex. The AIProcessingService will handle
     * format compatibility through proper content-type headers.
     * @param inputFile The input audio file
     * @return Original file (no conversion needed for now)
     */
    fun convertToMp3(inputFile: File): File? {
        try {
            Log.d(TAG, "=== MP3 CONVERSION STARTED ===")
            Log.d(TAG, "Input file: ${inputFile.absolutePath}")
            
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist")
                return null
            }
            
            // For now, return the original file since MediaCodec MP3 encoding is complex
            // The AIProcessingService will handle format compatibility through proper headers
            Log.d(TAG, "=== USING ORIGINAL FILE (NO CONVERSION) ===")
            Log.d(TAG, "File will be sent with appropriate content-type headers")
            return inputFile
            
        } catch (e: Exception) {
            Log.e(TAG, "=== MP3 CONVERSION ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Converts audio file to WAV format using MediaCodec
     * @param inputFile Input audio file
     * @param outputFile Output WAV file
     * @return true if conversion successful, false otherwise
     */
    private fun convertToWav(inputFile: File, outputFile: File): Boolean {
        var mediaExtractor: MediaExtractor? = null
        var mediaCodec: MediaCodec? = null
        var outputStream: java.io.FileOutputStream? = null
        
        try {
            // Set up MediaExtractor
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(inputFile.absolutePath)
            
            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                Log.e(TAG, "No audio track found")
                return false
            }
            
            // Select audio track
            mediaExtractor.selectTrack(audioTrackIndex)
            val inputFormat = mediaExtractor.getTrackFormat(audioTrackIndex)
            
            Log.d(TAG, "Input format: $inputFormat")
            
            // Create MediaCodec decoder
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
            if (mimeType == null) {
                Log.e(TAG, "MIME type is null, cannot create decoder")
                return false
            }
            mediaCodec = MediaCodec.createDecoderByType(mimeType)
            mediaCodec.configure(inputFormat, null, null, 0)
            mediaCodec.start()
            
            // Write WAV header
            outputStream = java.io.FileOutputStream(outputFile)
            writeWavHeader(outputStream, inputFormat)
            
            // Convert audio data
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            
            while (!isEOS) {
                val inputBufferIndex = mediaCodec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                        
                        if (sampleSize < 0) {
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.sampleTime, 0)
                            mediaExtractor.advance()
                        }
                    } else {
                        Log.e(TAG, "Input buffer is null")
                        return false
                    }
                }
                
                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)
                        outputStream.write(data)
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
            
            // Update WAV header with actual file size
            outputStream.close()
            updateWavHeader(outputFile)
            
            Log.d(TAG, "WAV conversion completed successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during WAV conversion", e)
            return false
        } finally {
            try {
                mediaCodec?.stop()
                mediaCodec?.release()
                mediaExtractor?.release()
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up MediaCodec resources", e)
            }
        }
    }
    
    /**
     * Writes WAV header to output stream
     */
    private fun writeWavHeader(outputStream: java.io.FileOutputStream, format: MediaFormat) {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val bitsPerSample = 16 // We'll convert to 16-bit PCM
        
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        
        // Write WAV header (placeholder - will be updated later)
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        buffer.put("RIFF".toByteArray(), 0, 4)
        buffer.putInt(0) // File size - 8 (placeholder)
        buffer.put("WAVE".toByteArray(), 0, 4)
        
        // fmt chunk
        buffer.put("fmt ".toByteArray(), 0, 4)
        buffer.putInt(16) // fmt chunk size
        buffer.putShort(1) // Audio format (PCM)
        buffer.putShort(channelCount.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        
        // data chunk
        buffer.put("data".toByteArray(), 0, 4)
        buffer.putInt(0) // Data size (placeholder)
        
        outputStream.write(header)
    }
    
    /**
     * Updates WAV header with actual file size
     */
    private fun updateWavHeader(wavFile: File) {
        try {
            val fileSize = wavFile.length()
            val dataSize = fileSize - 44
            
            val randomAccessFile = java.io.RandomAccessFile(wavFile, "rw")
            
            // Update file size
            randomAccessFile.seek(4)
            randomAccessFile.writeInt((fileSize - 8).toInt())
            
            // Update data size
            randomAccessFile.seek(40)
            randomAccessFile.writeInt(dataSize.toInt())
            
            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }
    
    /**
     * Cleans up converted files
     * @param files List of files to delete
     */
    fun cleanupConvertedFiles(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Cleaned up file: ${file.name}, deleted: $deleted")
            }
        }
    }
} 