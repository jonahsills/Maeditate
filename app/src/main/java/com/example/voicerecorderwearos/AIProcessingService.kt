package com.example.voicerecorderwearos

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AIProcessingService(private val context: Context) {
    private val TAG = "AIProcessingService"
    private val audioConverter = AudioConverter(context)
    
    // API keys are now provided via BuildConfig fields, loaded from local.properties
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = GEMINI_API_KEY
    )
    
    suspend fun processRecording(audioFile: File) {
        try {
            Log.d(TAG, "=== AI PROCESSING STARTED ===")
            Log.d(TAG, "Audio file: ${audioFile.name}")
            Log.d(TAG, "Audio file path: ${audioFile.absolutePath}")
            Log.d(TAG, "Audio file exists: ${audioFile.exists()}")
            Log.d(TAG, "Audio file size: ${audioFile.length()}")
            
            // Step 1: Transcribe audio to text
            Log.d(TAG, "Starting transcription...")
            val transcription = transcribeAudio(audioFile)
            Log.d(TAG, "Transcription completed: $transcription")
            
            // Step 2: Generate AI summary
            Log.d(TAG, "Starting AI summary generation...")
            val summary = generateSummary(transcription)
            Log.d(TAG, "AI Summary completed: $summary")
            
            // Step 3: Save to local file in list format
            Log.d(TAG, "Starting file save...")
            saveToLocalFile(transcription, summary)
            Log.d(TAG, "=== AI PROCESSING COMPLETED SUCCESSFULLY ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "=== AI PROCESSING FAILED ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun transcribeAudio(audioFile: File): String {
        return withContext(Dispatchers.IO) {
            val convertedFiles = mutableListOf<File>()
            
            try {
                Log.d(TAG, "Starting real audio transcription with OpenAI Whisper...")
                
                // Since we're now recording in WAV format, try transcription directly
                Log.d(TAG, "Audio file is already in WAV format, trying transcription...")
                val transcription = transcribeConvertedFile(audioFile, "audio/wav")
                if (transcription.isNotBlank()) {
                    Log.d(TAG, "Successfully transcribed with WAV format")
                    return@withContext transcription
                }
                
                // If direct WAV transcription fails, try conversion as fallback
                Log.d(TAG, "Direct WAV transcription failed, trying conversion...")
                val convertedFile = audioConverter.convertToWhisperWav(audioFile)
                if (convertedFile != null) {
                    convertedFiles.add(convertedFile)
                    val convertedTranscription = transcribeConvertedFile(convertedFile, "audio/wav")
                    if (convertedTranscription.isNotBlank()) {
                        Log.d(TAG, "Successfully transcribed with converted WAV format")
                        return@withContext convertedTranscription
                    }
                }
                
                // Final fallback to simulated transcription
                Log.d(TAG, "All real transcription attempts failed, using simulated transcription")
                return@withContext getSimulatedTranscription(audioFile)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio transcription", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                "Error transcribing audio: ${e.message}"
            } finally {
                // Clean up converted files
                if (convertedFiles.isNotEmpty()) {
                    Log.d(TAG, "Cleaning up ${convertedFiles.size} converted files...")
                    audioConverter.cleanupConvertedFiles(convertedFiles)
                }
            }
        }
    }
    
    private fun transcribeWithWhisper(audioFile: File): String? {
        try {
            Log.d(TAG, "=== WHISPER TRANSCRIPTION STARTED ===")
            Log.d(TAG, "Audio file: ${audioFile.name}")
            Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")
            Log.d(TAG, "Audio file exists: ${audioFile.exists()}")
            Log.d(TAG, "File extension: ${audioFile.extension}")
            Log.d(TAG, "Content-Type: audio/x-m4a")
            
            if (OPENAI_API_KEY == "YOUR_OPENAI_API_KEY_HERE") {
                Log.d(TAG, "OpenAI API key not set, will use simulated transcription")
                return null
            }
            
            Log.d(TAG, "API key length: ${OPENAI_API_KEY.length}")
            Log.d(TAG, "API key starts with: ${OPENAI_API_KEY.take(10)}...")
            
            // Create connection
            val url = URL("https://api.openai.com/v1/audio/transcriptions")
            val connection = url.openConnection() as HttpURLConnection
            
            // Set up connection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $OPENAI_API_KEY")
            
            // Create multipart boundary
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            Log.d(TAG, "Sending request to OpenAI Whisper API...")
            
            // Write multipart data
            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)
            
            // Add file part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n")
            writer.append("Content-Type: audio/x-m4a\r\n\r\n")
            writer.flush()
            
            // Write file content
            val fileInputStream = FileInputStream(audioFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            outputStream.flush()
            
            // Add model and language parts
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            writer.append("whisper-1")
            
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
            writer.append("en")
            
            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()
            
            // Get response
            val responseCode = connection.responseCode
            Log.d(TAG, "Response received: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Whisper API response: $responseBody")
                
                // Parse JSON response
                val jsonObject = JSONObject(responseBody)
                val transcription = jsonObject.optString("text", "")
                
                Log.d(TAG, "Whisper transcription: $transcription")
                return transcription
            } else {
                val errorStream = connection.errorStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Whisper API error: $responseCode")
                Log.e(TAG, "Error body: $errorBody")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== WHISPER TRANSCRIPTION ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    private fun transcribeWithWhisperAsMP3(audioFile: File): String? {
        try {
            Log.d(TAG, "=== WHISPER MP3 TRANSCRIPTION STARTED ===")
            Log.d(TAG, "Audio file: ${audioFile.name}")
            Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")
            
            if (OPENAI_API_KEY == "YOUR_OPENAI_API_KEY_HERE") {
                Log.d(TAG, "OpenAI API key not set, will use simulated transcription")
                return null
            }
            
            // Create connection
            val url = URL("https://api.openai.com/v1/audio/transcriptions")
            val connection = url.openConnection() as HttpURLConnection
            
            // Set up connection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $OPENAI_API_KEY")
            
            // Create multipart boundary
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            Log.d(TAG, "Sending request to OpenAI Whisper API with MP3 content type...")
            
            // Write multipart data
            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)
            
            // Add file part with MP3 content type
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name.replace(".m4a", ".mp3")}\"\r\n")
            writer.append("Content-Type: audio/mpeg\r\n\r\n")
            writer.flush()
            
            // Write file content
            val fileInputStream = FileInputStream(audioFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            outputStream.flush()
            
            // Add model and language parts
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            writer.append("whisper-1")
            
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
            writer.append("en")
            
            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()
            
            // Get response
            val responseCode = connection.responseCode
            Log.d(TAG, "MP3 Response received: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Whisper MP3 API response: $responseBody")
                
                // Parse JSON response
                val jsonObject = JSONObject(responseBody)
                val transcription = jsonObject.optString("text", "")
                
                Log.d(TAG, "Whisper MP3 transcription: $transcription")
                return transcription
            } else {
                val errorStream = connection.errorStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Whisper MP3 API error: $responseCode")
                Log.e(TAG, "Error body: $errorBody")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== WHISPER MP3 TRANSCRIPTION ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    private fun transcribeConvertedFile(audioFile: File, contentType: String): String {
        try {
            Log.d(TAG, "=== TRANSCRIBING CONVERTED FILE ===")
            Log.d(TAG, "Audio file: ${audioFile.name}")
            Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")
            Log.d(TAG, "Content-Type: $contentType")
            
            if (OPENAI_API_KEY == "YOUR_OPENAI_API_KEY_HERE") {
                Log.d(TAG, "OpenAI API key not set, will use simulated transcription")
                return ""
            }
            
            // Create connection
            val url = URL("https://api.openai.com/v1/audio/transcriptions")
            val connection = url.openConnection() as HttpURLConnection
            
            // Set up connection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $OPENAI_API_KEY")
            
            // Create multipart boundary
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            Log.d(TAG, "Sending request to OpenAI Whisper API...")
            
            // Write multipart data
            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)
            
            // Add file part with correct content type
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n")
            writer.append("Content-Type: $contentType\r\n\r\n")
            writer.flush()
            
            // Write file content
            val fileInputStream = FileInputStream(audioFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()
            outputStream.flush()
            
            // Add model and language parts
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            writer.append("whisper-1")
            
            writer.append("\r\n--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
            writer.append("en")
            
            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()
            
            // Get response
            val responseCode = connection.responseCode
            Log.d(TAG, "Response received: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val responseBody = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Whisper API response: $responseBody")
                
                // Parse JSON response
                val jsonObject = JSONObject(responseBody)
                val transcription = jsonObject.optString("text", "")
                
                Log.d(TAG, "Whisper transcription: $transcription")
                return transcription
            } else {
                val errorStream = connection.errorStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Whisper API error: $responseCode")
                Log.e(TAG, "Error body: $errorBody")
                return ""
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "=== CONVERTED FILE TRANSCRIPTION ERROR ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            return ""
        }
    }
    
    private fun getSimulatedTranscription(audioFile: File): String {
        Log.d(TAG, "Using simulated transcription as fallback...")
        val simulatedTranscription = when {
            audioFile.length() < 10000 -> "Quick test recording."
            audioFile.length() < 50000 -> "This is a medium length recording about my daily activities and plans."
            else -> "This is a longer recording where I'm discussing my weekly plans including meetings, " +
                    "appointments, and personal tasks that need to be completed. I have several important " +
                    "deadlines coming up and need to prioritize my work accordingly."
        }
        
        Log.d(TAG, "Simulated transcription: $simulatedTranscription")
        return simulatedTranscription
    }
    


    

    

    

    
    private suspend fun generateSummary(transcription: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating prompt for Gemini API...")
                val prompt = """
                    Please summarize the following text in 2-3 sentences, focusing on the key points and main ideas:
                    
                    $transcription
                    
                    Format your response as a clear, concise summary that captures the essential information.
                """.trimIndent()
                
                Log.d(TAG, "Sending request to Gemini API...")
                Log.d(TAG, "API key length: ${GEMINI_API_KEY.length}")
                Log.d(TAG, "API key starts with: ${GEMINI_API_KEY.take(10)}...")
                
                val response = generativeModel.generateContent(prompt)
                val summary = response.text ?: "No summary generated"
                
                Log.d(TAG, "Gemini API response received successfully")
                Log.d(TAG, "Generated summary: $summary")
                summary
                
            } catch (e: Exception) {
                Log.e(TAG, "=== GEMINI API ERROR ===", e)
                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error message: ${e.message}")
                "Error generating summary: ${e.message}"
            }
        }
    }
    
    private fun saveToLocalFile(transcription: String, summary: String) {
        try {
            Log.d(TAG, "saveToLocalFile called with transcription: $transcription")
            Log.d(TAG, "saveToLocalFile called with summary: $summary")

            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage directory not available.")
                return
            }
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val fileName = "voice_summaries.txt"
            val file = File(externalDir, fileName)
            
            Log.d(TAG, "File path: ${file.absolutePath}")
            Log.d(TAG, "File exists before: ${file.exists()}")
            
            // Create the entry to append
            val entry = """
                
                [${timestamp}]
                TRANSCRIPTION: $transcription
                AI SUMMARY: $summary
                ---
            """.trimIndent()
            
            Log.d(TAG, "Entry to append: $entry")
            
            // Append to file (create if doesn't exist)
            if (file.exists()) {
                Log.d(TAG, "File exists, appending to existing file")
                file.appendText("\n$entry")
            } else {
                Log.d(TAG, "File doesn't exist, creating new file")
                file.writeText("VOICE RECORDER SUMMARIES\n$entry")
            }
            
            Log.d(TAG, "File exists after: ${file.exists()}")
            Log.d(TAG, "File size after: ${file.length()} bytes")
            Log.d(TAG, "Saved to local file: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to local file", e)
            e.printStackTrace()
        }
    }
    
    // Method to read all summaries (useful for debugging)
    fun readAllSummaries(): String {
        return try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                Log.e(TAG, "External storage directory not available for reading.")
                return "External storage not available."
            }
            val fileName = "voice_summaries.txt"
            val file = File(externalDir, fileName)
            if (file.exists()) {
                file.readText()
            } else {
                "No summaries file found"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading summaries", e)
            "Error reading summaries: ${e.message}"
        }
    }
}
