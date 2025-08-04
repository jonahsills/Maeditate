package com.example.voicerecorderwearos

import android.app.*
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VoiceRecorderService : Service() {
    
    companion object {
        private const val TAG = "VoiceRecorderService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "voice_recorder_channel"
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var pcmWavRecorder: PcmWavRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    private val aiProcessingService = AIProcessingService(this)
    private val audioTestHelper = AudioTestHelper(this)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): VoiceRecorderService = this@VoiceRecorderService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RECORDING" -> startRecording()
            "STOP_RECORDING" -> stopRecording()
        }
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Recorder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Voice recording service"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun startRecording() {
        if (isRecording) return
        
        try {
            // Create output file (WAV format for guaranteed compatibility)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "recording_$timestamp.wav"
            outputFile = File(getExternalFilesDir(null), fileName)
            
            Log.d(TAG, "=== RECORDING START ===")
            Log.d(TAG, "Output file: ${outputFile?.absolutePath}")
            Log.d(TAG, "External files dir: ${getExternalFilesDir(null)?.absolutePath}")
            
            // Try PCM WAV recorder first, fallback to MediaRecorder if it fails
            Log.d(TAG, "Creating PcmWavRecorder...")
            pcmWavRecorder = PcmWavRecorder(
                sampleRate = 16000,
                channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO,
                audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT,
                audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
            )
            
            Log.d(TAG, "Starting PcmWavRecorder...")
            val success = pcmWavRecorder?.start(outputFile!!)
            Log.d(TAG, "PcmWavRecorder start result: $success")
            
            if (success == true) {
                isRecording = true
                
                // Start foreground service with notification
                startForeground(NOTIFICATION_ID, createNotification())
                
                Log.d(TAG, "PCM WAV recording started successfully: ${outputFile?.absolutePath}")
                Log.d(TAG, "Recording settings: 16kHz + Mono + 16-bit PCM")
            } else {
                Log.e(TAG, "Failed to start PCM WAV recording, trying MediaRecorder fallback...")
                
                // Fallback to MediaRecorder
                try {
                    mediaRecorder = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128000)
                        setAudioSamplingRate(16000)
                        setAudioChannels(1)
                        setOutputFile(outputFile?.absolutePath)
                        
                        Log.d(TAG, "Preparing MediaRecorder fallback...")
                        prepare()
                        Log.d(TAG, "Starting MediaRecorder fallback...")
                        start()
                        isRecording = true
                        
                        // Start foreground service with notification
                        startForeground(NOTIFICATION_ID, createNotification())
                        
                        Log.d(TAG, "MediaRecorder fallback started successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MediaRecorder fallback also failed", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PCM WAV recorder", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        Log.d(TAG, "=== RECORDING STOP ===")
        
        try {
            if (pcmWavRecorder != null) {
                Log.d(TAG, "Stopping PCM WAV recorder...")
                pcmWavRecorder?.stop()
                pcmWavRecorder = null
            } else if (mediaRecorder != null) {
                Log.d(TAG, "Stopping MediaRecorder fallback...")
                mediaRecorder?.apply {
                    try {
                        stop()
                        Log.d(TAG, "MediaRecorder stopped successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping MediaRecorder", e)
                    } finally {
                        try {
                            release()
                            Log.d(TAG, "MediaRecorder released successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing MediaRecorder", e)
                        }
                    }
                }
                mediaRecorder = null
            }
            
            isRecording = false
            
            // Wait a moment for file to be fully written
            Thread.sleep(500)
            
            Log.d(TAG, "Recording stopped: ${outputFile?.absolutePath}")
            Log.d(TAG, "Final file size: ${outputFile?.length()} bytes")
            Log.d(TAG, "File exists: ${outputFile?.exists()}")
            Log.d(TAG, "File can read: ${outputFile?.canRead()}")
            Log.d(TAG, "File can write: ${outputFile?.canWrite()}")
            
            // Verify file is not empty
            if (outputFile?.length() ?: 0 < 1024) {
                Log.e(TAG, "WARNING: File size is very small - recording may be silent!")
            }
            
            // Test the recorded audio file
            outputFile?.let { file ->
                Log.d(TAG, "=== AUDIO FILE TESTING ===")
                val fileInfo = audioTestHelper.getAudioFileInfo(file)
                Log.d(TAG, "Audio file info:\n$fileInfo")
                
                val playbackTest = audioTestHelper.testAudioPlayback(file)
                Log.d(TAG, "Audio playback test result: $playbackTest")
                
                if (!playbackTest) {
                    Log.e(TAG, "WARNING: Audio file failed playback test - recording may be silent!")
                }
            }
            
            // Process with AI after recording stops
            outputFile?.let { file ->
                Log.d(TAG, "About to start AI processing for file: ${file.absolutePath}")
                Log.d(TAG, "File exists: ${file.exists()}, File size: ${file.length()}")
                
                serviceScope.launch {
                    try {
                        Log.d(TAG, "Starting AI processing for recording")
                        aiProcessingService.processRecording(file)
                        Log.d(TAG, "AI processing completed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in AI processing", e)
                        e.printStackTrace()
                    } finally {
                        // Stop foreground service and self AFTER AI processing is done
                        stopForeground(true)
                        stopSelf()
                    }
                }
            } ?: run {
                Log.e(TAG, "outputFile is null, cannot process with AI")
                // Stop service immediately if no file to process
                stopForeground(true)
                stopSelf()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            e.printStackTrace()
            stopForeground(true)
            stopSelf()
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Recorder")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
} 