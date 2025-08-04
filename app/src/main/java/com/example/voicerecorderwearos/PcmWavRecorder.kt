package com.example.voicerecorderwearos

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWavRecorder(
    private val sampleRate: Int = 16_000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC
) {
    private val TAG = "PcmWavRecorder"
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null

    fun start(outputWav: File): Boolean {
        return try {
            Log.d(TAG, "=== PCM WAV RECORDING STARTED ===")
            Log.d(TAG, "Output file: ${outputWav.absolutePath}")
            Log.d(TAG, "Sample rate: $sampleRate Hz")
            Log.d(TAG, "Channels: ${if (channelConfig == AudioFormat.CHANNEL_IN_MONO) "Mono" else "Stereo"}")
            Log.d(TAG, "Audio format: 16-bit PCM")
            
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBuf, sampleRate) // a little headroom
            Log.d(TAG, "Buffer size: $bufferSize bytes")
            
            audioRecord = AudioRecord(
                audioSource,
                sampleRate, channelConfig, audioFormat, bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                Log.e(TAG, "AudioRecord state: ${audioRecord?.state}")
                return false
            }
            
            Log.d(TAG, "AudioRecord initialized successfully")
            Log.d(TAG, "AudioRecord state: ${audioRecord?.state}")
            Log.d(TAG, "AudioRecord audio session ID: ${audioRecord?.audioSessionId}")
            
            val pcmFile = File(outputWav.parentFile, outputWav.nameWithoutExtension + ".pcm")
            val os = BufferedOutputStream(FileOutputStream(pcmFile))
            
            audioRecord!!.startRecording()
            isRecording = true
            
            Log.d(TAG, "AudioRecord started recording")
            
            recordingThread = Thread {
                val buf = ByteArray(bufferSize)
                var totalBytes = 0L
                var peakAmplitude = 0
                
                while (isRecording) {
                    val read = audioRecord!!.read(buf, 0, buf.size)
                    if (read > 0) {
                        os.write(buf, 0, read)
                        totalBytes += read
                        
                        // Calculate peak amplitude for debugging
                        val peak = peak16(buf, read)
                        peakAmplitude = maxOf(peakAmplitude, peak)
                        
                        // Log peak every 1000 samples (about every 2 seconds)
                        if (totalBytes % (sampleRate * 2) < read) {
                            Log.d(TAG, "Peak amplitude: $peak, Total bytes: $totalBytes, Read: $read")
                        }
                        
                        // Log if we're getting all zeros (silence)
                        if (peak == 0) {
                            Log.w(TAG, "WARNING: Peak amplitude is 0 - recording may be silent!")
                        }
                    } else if (read == 0) {
                        Log.w(TAG, "AudioRecord.read() returned 0 - no data available")
                    } else {
                        Log.e(TAG, "AudioRecord.read() returned negative value: $read")
                    }
                }
                
                os.flush()
                os.close()
                
                Log.d(TAG, "Recording stopped. Total bytes: $totalBytes, Peak amplitude: $peakAmplitude")
                
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                
                // Wrap PCM -> WAV
                if (totalBytes > 0) {
                    pcmToWav(pcmFile, outputWav, sampleRate, 1, 16, totalBytes)
                    pcmFile.delete()
                    Log.d(TAG, "WAV file created successfully: ${outputWav.length()} bytes")
                } else {
                    Log.e(TAG, "No audio data recorded!")
                }
            }
            
            recordingThread?.start()
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting PCM WAV recording", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping PCM WAV recording...")
        isRecording = false
        recordingThread?.join(5000) // Wait up to 5 seconds for thread to finish
        Log.d(TAG, "PCM WAV recording stopped")
    }

    private fun pcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int, bitsPerSample: Int, dataSize: Long) {
        try {
            Log.d(TAG, "Converting PCM to WAV...")
            Log.d(TAG, "PCM file size: ${pcmFile.length()} bytes")
            Log.d(TAG, "Data size: $dataSize bytes")
            
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            
            RandomAccessFile(wavFile, "rw").use { out ->
                // RIFF header
                out.writeBytes("RIFF")
                out.writeIntLE((36 + dataSize).toInt())
                out.writeBytes("WAVE")
                
                // fmt chunk
                out.writeBytes("fmt ")
                out.writeIntLE(16)                 // Subchunk1Size for PCM
                out.writeShortLE(1)                // PCM format
                out.writeShortLE(channels.toShort())
                out.writeIntLE(sampleRate)
                out.writeIntLE(byteRate)
                out.writeShortLE(blockAlign.toShort()) // block align
                out.writeShortLE(bitsPerSample.toShort())
                
                // data chunk
                out.writeBytes("data")
                out.writeIntLE(dataSize.toInt())

                // Copy PCM data
                FileInputStream(pcmFile).use { ins ->
                    val buf = ByteArray(4096)
                    var r: Int
                    while (ins.read(buf).also { r = it } > 0) {
                        out.write(buf, 0, r)
                    }
                }
            }
            
            Log.d(TAG, "WAV conversion completed. File size: ${wavFile.length()} bytes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PCM to WAV", e)
            throw e
        }
    }

    // Calculate peak amplitude for debugging
    private fun peak16(buf: ByteArray, len: Int): Int {
        var peak = 0
        var i = 0
        while (i < len - 1) {
            val s = ((buf[i+1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            peak = maxOf(peak, kotlin.math.abs(s))
            i += 2
        }
        return peak
    }

    // Helpers for little-endian writing
    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        ))
    }
    
    private fun RandomAccessFile.writeShortLE(v: Short) {
        write(byteArrayOf(
            (v.toInt() and 0xFF).toByte(),
            ((v.toInt() shr 8) and 0xFF).toByte()
        ))
    }
} 