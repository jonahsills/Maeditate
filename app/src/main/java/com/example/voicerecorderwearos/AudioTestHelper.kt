package com.example.voicerecorderwearos

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioTestHelper(private val context: Context) {
    private val TAG = "AudioTestHelper"
    
    /**
     * Tests if an audio file can be played back
     * @param audioFile The audio file to test
     * @return true if the file can be played, false otherwise
     */
    fun testAudioPlayback(audioFile: File): Boolean {
        return try {
            Log.d(TAG, "=== AUDIO PLAYBACK TEST ===")
            Log.d(TAG, "Testing file: ${audioFile.absolutePath}")
            Log.d(TAG, "File size: ${audioFile.length()} bytes")
            Log.d(TAG, "File exists: ${audioFile.exists()}")
            
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "File does not exist or is empty")
                return false
            }
            
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
            
            val duration = mediaPlayer.duration
            Log.d(TAG, "Audio duration: $duration ms")
            
            if (duration <= 0) {
                Log.e(TAG, "Audio file has no duration - likely silent or corrupted")
                mediaPlayer.release()
                return false
            }
            
            // Test playback for a short duration
            mediaPlayer.start()
            Thread.sleep(1000) // Play for 1 second
            mediaPlayer.stop()
            mediaPlayer.release()
            
            Log.d(TAG, "Audio playback test successful")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback test failed", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Gets audio file information
     * @param audioFile The audio file to analyze
     * @return String with file information
     */
    fun getAudioFileInfo(audioFile: File): String {
        return try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioFile.absolutePath)
            mediaPlayer.prepare()
            
            val duration = mediaPlayer.duration
            val info = """
                File: ${audioFile.name}
                Path: ${audioFile.absolutePath}
                Size: ${audioFile.length()} bytes
                Duration: $duration ms
                Exists: ${audioFile.exists()}
                Can Read: ${audioFile.canRead()}
                Can Write: ${audioFile.canWrite()}
            """.trimIndent()
            
            mediaPlayer.release()
            info
            
        } catch (e: Exception) {
            """
                File: ${audioFile.name}
                Path: ${audioFile.absolutePath}
                Size: ${audioFile.length()} bytes
                Error: ${e.message}
                Exists: ${audioFile.exists()}
                Can Read: ${audioFile.canRead()}
                Can Write: ${audioFile.canWrite()}
            """.trimIndent()
        }
    }
} 