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
    private val aiProcessingService = AIProcessingService(this)
    private var isRecording = false
    private var outputFile: File? = null
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
            "SAVE_RECORDING" -> saveRecording()
            "DELETE_RECORDING" -> deleteRecording()
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
        
        // Start foreground immediately with placeholder notification to avoid 5-second ANR
        startForeground(NOTIFICATION_ID, createNotification())
        
        try {
            Log.d(TAG, "=== RECORDING START ===")
            
            // Create output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "recording_$timestamp.wav"
            outputFile = File(getExternalFilesDir(null), fileName)
            
            Log.d(TAG, "Output file: ${outputFile?.absolutePath}")
            
            // Use simple MediaRecorder for now (more reliable)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(outputFile?.absolutePath)
                
                Log.d(TAG, "Preparing MediaRecorder...")
                prepare()
                Log.d(TAG, "Starting MediaRecorder...")
                start()
            }
            
            isRecording = true
            
            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification())
            
            Log.d(TAG, "Recording started successfully: ${outputFile?.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            e.printStackTrace()
            // Clean up on error
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            // Stop foreground and self to avoid the system killing the app
            stopForeground(true)
            stopSelf()
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        Log.d(TAG, "=== RECORDING STOP ===")
        
        try {
            if (mediaRecorder != null) {
                Log.d(TAG, "Stopping MediaRecorder...")
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
            
            // Stop foreground but keep service running while user decides
            stopForeground(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            e.printStackTrace()
            stopForeground(true)
            stopSelf()
        }
    }
    
    private fun saveRecording() {
        outputFile?.let { file ->
            Log.d(TAG, "Saving recording: ${file.absolutePath}")
            serviceScope.launch {
                aiProcessingService.processRecording(file)
                // Delete raw audio after summary is saved
                if (file.exists()) file.delete()
                outputFile = null
                stopSelf()
            }
        }
    }
    
    private fun deleteRecording() {
        outputFile?.let { file ->
            Log.d(TAG, "Deleting recording: ${file.absolutePath}")
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Recording file deleted")
            }
        }
        outputFile = null
        stopSelf()
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