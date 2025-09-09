package com.example.voicerecorderwearos

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

class BackendService(private val context: Context) {
    private val TAG = "BackendService"
    
    // Backend configuration
    private val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
    private val deviceModel = "Wear OS Watch"
    
    // Authentication
    private var authToken: String? = null
    private var userId: String? = null
    private var deviceId: String? = null
    
    // Offline queue for failed requests
    private val offlineQueue = mutableListOf<QueuedRequest>()
    
    data class QueuedRequest(
        val type: String,
        val payload: String,
        val timestamp: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    )
    
    data class AuthResponse(
        val token: String,
        val userId: String,
        val deviceId: String,
        val expiresInSec: Long
    )
    
    data class UploadInitResponse(
        val sessionId: String,
        val uploadUrl: String,
        val audioUrl: String
    )
    
    data class TranscriptResponse(
        val ok: Boolean,
        val sessionId: String,
        val transcriptId: String,
        val status: String
    )
    
    data class TranscriptStatusResponse(
        val id: String,
        val status: String,
        val sessionId: String,
        val text: String? = null,
        val language: String? = null,
        val confidence: Double? = null,
        val summary: SummaryData? = null,
        val error: String? = null
    )
    
    data class SummaryData(
        val id: String,
        val model: String,
        val text: String
    )
    
    /**
     * Initialize authentication with the backend
     */
    suspend fun initializeAuth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing authentication with backend...")
                
                // Check if we have cached auth data
                val prefs = context.getSharedPreferences("backend_auth", Context.MODE_PRIVATE)
                val cachedToken = prefs.getString("auth_token", null)
                val cachedUserId = prefs.getString("user_id", null)
                val cachedDeviceId = prefs.getString("device_id", null)
                val tokenExpiry = prefs.getLong("token_expiry", 0)
                
                // Use cached token if still valid
                if (cachedToken != null && cachedUserId != null && cachedDeviceId != null && 
                    System.currentTimeMillis() < tokenExpiry) {
                    authToken = cachedToken
                    userId = cachedUserId
                    deviceId = cachedDeviceId
                    Log.d(TAG, "Using cached authentication")
                    return@withContext true
                }
                
                // Get new token from backend
                val authResponse = authenticateWithBackend()
                if (authResponse != null) {
                    authToken = authResponse.token
                    userId = authResponse.userId
                    deviceId = authResponse.deviceId
                    
                    // Cache the auth data
                    val editor = prefs.edit()
                    editor.putString("auth_token", authResponse.token)
                    editor.putString("user_id", authResponse.userId)
                    editor.putString("device_id", authResponse.deviceId)
                    editor.putLong("token_expiry", System.currentTimeMillis() + (authResponse.expiresInSec * 1000))
                    editor.apply()
                    
                    Log.d(TAG, "Authentication successful")
                    return@withContext true
                }
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Authentication failed", e)
                false
            }
        }
    }
    
    /**
     * Process audio recording through the backend
     */
    suspend fun processRecording(audioFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing recording through backend...")
                
                // Ensure we're authenticated
                if (!initializeAuth()) {
                    throw Exception("Failed to authenticate with backend")
                }
                
                // Step 1: Get upload URL
                val uploadResponse = getUploadUrl(audioFile.extension)
                Log.d(TAG, "Got upload URL: ${uploadResponse.uploadUrl}")
                
                // Step 2: Upload audio file
                val uploadSuccess = uploadAudioFile(audioFile, uploadResponse.uploadUrl)
                if (!uploadSuccess) {
                    throw Exception("Failed to upload audio file")
                }
                Log.d(TAG, "Audio file uploaded successfully")
                
                // Step 3: Submit for transcription
                val transcriptResponse = submitForTranscription(
                    uploadResponse.sessionId,
                    uploadResponse.audioUrl
                )
                Log.d(TAG, "Submitted for transcription: ${transcriptResponse.transcriptId}")
                
                // Step 4: Poll for completion
                val result = pollForCompletion(transcriptResponse.transcriptId)
                Log.d(TAG, "Processing completed: ${result.status}")
                
                if (result.status == "COMPLETE") {
                    val summary = result.summary?.text ?: "No summary available"
                    val transcription = result.text ?: "No transcription available"
                    
                    // Save to local file
                    saveToLocalFile(transcription, summary)
                    
                    "Transcription: $transcription\n\nSummary: $summary"
                } else if (result.status == "FAILED") {
                    throw Exception("Backend processing failed: ${result.error}")
                } else {
                    throw Exception("Unexpected status: ${result.status}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Backend processing failed", e)
                
                // Queue for retry if it's a network error
                if (isNetworkError(e)) {
                    queueForRetry("process_recording", audioFile.absolutePath)
                }
                
                throw e
            }
        }
    }
    
    /**
     * Authenticate with backend and get JWT token
     */
    private suspend fun authenticateWithBackend(): AuthResponse? {
        return try {
            val url = URL("$BACKEND_BASE_URL/auth/anonymous")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            val requestBody = JSONObject().apply {
                put("deviceModel", deviceModel)
            }.toString()
            
            val outputStream = connection.outputStream
            outputStream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            outputStream.close()
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)
                
                AuthResponse(
                    token = json.getString("token"),
                    userId = json.getString("userId"),
                    deviceId = json.getString("deviceId"),
                    expiresInSec = json.getLong("expiresInSec")
                )
            } else {
                Log.e(TAG, "Auth failed with code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth request failed", e)
            null
        }
    }
    
    /**
     * Get presigned upload URL from backend
     */
    private suspend fun getUploadUrl(fileExtension: String): UploadInitResponse {
        val url = URL("$BACKEND_BASE_URL/v1/upload-init")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        
        val requestBody = JSONObject().apply {
            put("fileExt", fileExtension)
            put("contentType", "audio/mp4")
        }.toString()
        
        val outputStream = connection.outputStream
        outputStream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
        outputStream.close()
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            
            UploadInitResponse(
                sessionId = json.getString("sessionId"),
                uploadUrl = json.getString("uploadUrl"),
                audioUrl = json.getString("audioUrl")
            )
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Upload init failed: $responseCode - $errorBody")
        }
    }
    
    /**
     * Upload audio file to S3 using presigned URL
     */
    private suspend fun uploadAudioFile(audioFile: File, uploadUrl: String): Boolean {
        return try {
            val url = URL(uploadUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "audio/mp4")
            
            val fileInputStream = FileInputStream(audioFile)
            val outputStream = connection.outputStream
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            fileInputStream.close()
            outputStream.close()
            
            val responseCode = connection.responseCode
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "File upload failed", e)
            false
        }
    }
    
    /**
     * Submit audio for transcription
     */
    private suspend fun submitForTranscription(sessionId: String, audioUrl: String): TranscriptResponse {
        val url = URL("$BACKEND_BASE_URL/v1/transcripts")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
        
        val requestBody = JSONObject().apply {
            put("sessionId", sessionId)
            put("audioUrl", audioUrl)
            put("wantSummary", true)
            put("meta", JSONObject().apply {
                put("device", deviceModel)
            })
        }.toString()
        
        val outputStream = connection.outputStream
        outputStream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
        outputStream.close()
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            
            TranscriptResponse(
                ok = json.getBoolean("ok"),
                sessionId = json.getString("sessionId"),
                transcriptId = json.getString("transcriptId"),
                status = json.getString("status")
            )
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Transcript submission failed: $responseCode - $errorBody")
        }
    }
    
    /**
     * Poll for transcription completion
     */
    private suspend fun pollForCompletion(transcriptId: String): TranscriptStatusResponse {
        var attempts = 0
        val maxAttempts = 30 // 30 attempts with 2-second intervals = 1 minute max
        
        while (attempts < maxAttempts) {
            try {
                val url = URL("$BACKEND_BASE_URL/v1/transcripts/$transcriptId")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $authToken")
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseBody)
                    
                    val status = json.getString("status")
                    
                    if (status == "COMPLETE" || status == "FAILED") {
                        val summary = if (json.has("summary")) {
                            val summaryJson = json.getJSONObject("summary")
                            SummaryData(
                                id = summaryJson.getString("id"),
                                model = summaryJson.getString("model"),
                                text = summaryJson.getString("text")
                            )
                        } else null
                        
                        return TranscriptStatusResponse(
                            id = json.getString("id"),
                            status = status,
                            sessionId = json.getString("sessionId"),
                            text = if (json.has("text")) json.getString("text") else null,
                            language = if (json.has("language")) json.getString("language") else null,
                            confidence = if (json.has("confidence")) json.getDouble("confidence") else null,
                            summary = summary,
                            error = if (json.has("error")) json.getString("error") else null
                        )
                    }
                }
                
                // Wait 2 seconds before next attempt
                kotlinx.coroutines.delay(2000)
                attempts++
                
            } catch (e: Exception) {
                Log.e(TAG, "Polling attempt $attempts failed", e)
                attempts++
                kotlinx.coroutines.delay(2000)
            }
        }
        
        throw Exception("Transcription polling timeout after $maxAttempts attempts")
    }
    
    /**
     * Save results to local file
     */
    private fun saveToLocalFile(transcription: String, summary: String) {
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage directory not available.")
                return
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val fileName = "voice_summaries.txt"
            val file = File(externalDir, fileName)
            
            val entry = """
                
                [${timestamp}]
                TRANSCRIPTION: $transcription
                AI SUMMARY: $summary
                ---
            """.trimIndent()
            
            if (file.exists()) {
                file.appendText("\n$entry")
            } else {
                file.writeText("VOICE RECORDER SUMMARIES\n$entry")
            }
            
            Log.d(TAG, "Saved to local file: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to local file", e)
        }
    }
    
    /**
     * Check if error is network-related
     */
    private fun isNetworkError(e: Exception): Boolean {
        return e.message?.contains("network", ignoreCase = true) == true ||
               e.message?.contains("timeout", ignoreCase = true) == true ||
               e.message?.contains("connection", ignoreCase = true) == true
    }
    
    /**
     * Queue request for retry when network is available
     */
    private fun queueForRetry(type: String, payload: String) {
        offlineQueue.add(QueuedRequest(type, payload))
        Log.d(TAG, "Queued request for retry: $type")
    }
    
    /**
     * Process queued requests when network is available
     */
    suspend fun processQueuedRequests() {
        if (offlineQueue.isEmpty()) return
        
        Log.d(TAG, "Processing ${offlineQueue.size} queued requests...")
        
        val iterator = offlineQueue.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            try {
                // Retry the request based on type
                when (request.type) {
                    "process_recording" -> {
                        val audioFile = File(request.payload)
                        if (audioFile.exists()) {
                            processRecording(audioFile)
                            iterator.remove()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for ${request.type}", e)
                // Remove if too many retries
                if (request.retryCount >= 3) {
                    iterator.remove()
                }
            }
        }
    }
}
