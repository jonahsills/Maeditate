package com.example.voicerecorderwearos

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File

class AudioSourceTest(private val context: Context) {
    private val TAG = "AudioSourceTest"
    
    /**
     * Tests different audio sources to find which one works
     * @return Map of audio source to success status
     */
    fun testAudioSources(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        
        val audioSources = listOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "DEFAULT" to MediaRecorder.AudioSource.DEFAULT,
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL
        )
        
        Log.d(TAG, "=== AUDIO SOURCE TESTING STARTED ===")
        
        for ((sourceName, audioSource) in audioSources) {
            Log.d(TAG, "Testing audio source: $sourceName")
            val success = testAudioSource(audioSource, sourceName)
            results[sourceName] = success
            Log.d(TAG, "Audio source $sourceName: ${if (success) "SUCCESS" else "FAILED"}")
        }
        
        Log.d(TAG, "=== AUDIO SOURCE TESTING COMPLETED ===")
        Log.d(TAG, "Results: $results")
        
        return results
    }
    
    private fun testAudioSource(audioSource: Int, sourceName: String): Boolean {
        var audioRecord: AudioRecord? = null
        
        return try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBuf, sampleRate)
            
            Log.d(TAG, "Creating AudioRecord for $sourceName...")
            audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed for $sourceName")
                Log.e(TAG, "State: ${audioRecord.state}")
                return false
            }
            
            Log.d(TAG, "AudioRecord initialized successfully for $sourceName")
            Log.d(TAG, "Audio session ID: ${audioRecord.audioSessionId}")
            
            // Test recording for a short time
            audioRecord.startRecording()
            Log.d(TAG, "Started recording with $sourceName")
            
            val buf = ByteArray(bufferSize)
            var totalBytes = 0L
            var peakAmplitude = 0
            var zeroCount = 0
            
            // Record for 1 second
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1000) {
                val read = audioRecord.read(buf, 0, buf.size)
                if (read > 0) {
                    totalBytes += read
                    val peak = peak16(buf, read)
                    peakAmplitude = maxOf(peakAmplitude, peak)
                    
                    if (peak == 0) {
                        zeroCount++
                    }
                }
            }
            
            audioRecord.stop()
            audioRecord.release()
            
            Log.d(TAG, "$sourceName test results:")
            Log.d(TAG, "  Total bytes: $totalBytes")
            Log.d(TAG, "  Peak amplitude: $peakAmplitude")
            Log.d(TAG, "  Zero amplitude samples: $zeroCount")
            
            // Consider successful if we got some data and some non-zero peaks
            val success = totalBytes > 0 && peakAmplitude > 0
            Log.d(TAG, "$sourceName test: ${if (success) "SUCCESS" else "FAILED"}")
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing audio source $sourceName", e)
            false
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord for $sourceName", e)
            }
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
} 