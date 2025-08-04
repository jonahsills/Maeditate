package com.example.voicerecorderwearos

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecordingTest(private val context: Context) {
    private val TAG = "AudioRecordingTest"
    
    /**
     * Performs a test recording to verify audio functionality
     * @return true if test recording was successful, false otherwise
     */
    fun performTestRecording(): Boolean {
        var mediaRecorder: MediaRecorder? = null
        var testFile: File? = null
        
        try {
            Log.d(TAG, "=== AUDIO RECORDING TEST STARTED ===")
            
            // Create test file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "test_recording_$timestamp.m4a"
            testFile = File(context.getExternalFilesDir(null), fileName)
            
            Log.d(TAG, "Test file: ${testFile.absolutePath}")
            
            // Initialize MediaRecorder with minimal configuration
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(testFile.absolutePath)
                
                Log.d(TAG, "Preparing test MediaRecorder...")
                prepare()
                Log.d(TAG, "Starting test recording...")
                start()
            }
            
            // Record for 3 seconds
            Log.d(TAG, "Recording for 3 seconds...")
            Thread.sleep(3000)
            
            // Stop recording
            Log.d(TAG, "Stopping test recording...")
            mediaRecorder.stop()
            mediaRecorder.release()
            mediaRecorder = null
            
            // Wait for file to be written
            Thread.sleep(200)
            
            // Verify file
            if (testFile.exists() && testFile.length() > 1024) {
                Log.d(TAG, "=== AUDIO RECORDING TEST SUCCESSFUL ===")
                Log.d(TAG, "Test file size: ${testFile.length()} bytes")
                
                // Test playback
                val audioTestHelper = AudioTestHelper(context)
                val playbackTest = audioTestHelper.testAudioPlayback(testFile)
                Log.d(TAG, "Test file playback result: $playbackTest")
                
                // Clean up test file
                testFile.delete()
                Log.d(TAG, "Test file cleaned up")
                
                return playbackTest
            } else {
                Log.e(TAG, "=== AUDIO RECORDING TEST FAILED ===")
                Log.e(TAG, "File exists: ${testFile.exists()}")
                Log.e(TAG, "File size: ${testFile.length()} bytes")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== AUDIO RECORDING TEST ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            try {
                mediaRecorder?.release()
                testFile?.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up test resources", e)
            }
        }
    }
    
    /**
     * Checks if audio recording permissions are available
     * @return true if permissions are available, false otherwise
     */
    fun checkAudioPermissions(): Boolean {
        return try {
            // Try to create a MediaRecorder to test permissions
            val testRecorder = MediaRecorder()
            testRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            testRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            testRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            testRecorder.setOutputFile("/dev/null")
            testRecorder.prepare()
            testRecorder.release()
            
            Log.d(TAG, "Audio permissions check: SUCCESS")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Audio permissions check: FAILED", e)
            Log.e(TAG, "Exception: ${e.message}")
            false
        }
    }
} 