package com.example.voicerecorderwearos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, can start recording
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceRecorderWearOSTheme {
                VoiceRecorderScreen(
                    onStartRecording = { startVoiceRecording() },
                    onStopRecording = { stopVoiceRecording() }
                )
            }
        }
    }
    
    private fun startVoiceRecording() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, start recording
                val intent = Intent(this, VoiceRecorderService::class.java).apply {
                    action = "START_RECORDING"
                }
                startForegroundService(intent)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show rationale if needed
                Log.d("MainActivity", "Should show permission rationale")
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun stopVoiceRecording() {
        val intent = Intent(this, VoiceRecorderService::class.java).apply {
            action = "STOP_RECORDING"
        }
        startService(intent)
    }
}

@Composable
fun VoiceRecorderWearOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = Color(0xFF2196F3),
            primaryVariant = Color(0xFF1976D2),
            secondary = Color(0xFFF44336),
            secondaryVariant = Color(0xFFD32F2F)
        ),
        content = content
    )
}

@Composable
fun VoiceRecorderScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRecording) "Recording..." else "Voice Recorder",
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                color = if (isRecording) Color.Red else MaterialTheme.colors.onSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    if (isRecording) {
                        onStopRecording()
                        isRecording = false
                    } else {
                        onStartRecording()
                        isRecording = true
                    }
                },
                colors = androidx.wear.compose.material.ButtonDefaults.buttonColors(
                    backgroundColor = if (isRecording) Color.Red else MaterialTheme.colors.primary
                ),
                modifier = Modifier.size(80.dp)
            ) {
                Text(
                    text = if (isRecording) "STOP" else "REC",
                    color = Color.White,
                    style = MaterialTheme.typography.body2
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isRecording) "Tap to stop" else "Tap to record",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Preview
@Composable
fun VoiceRecorderPreview() {
    VoiceRecorderWearOSTheme {
        VoiceRecorderScreen(
            onStartRecording = { /* Preview only */ },
            onStopRecording = { /* Preview only */ }
        )
    }
}

@Preview(name = "Recording State")
@Composable
fun VoiceRecorderRecordingPreview() {
    VoiceRecorderWearOSTheme {
        var isRecording by remember { mutableStateOf(true) }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Recording...",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    color = Color.Red
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { isRecording = !isRecording },
                    colors = androidx.wear.compose.material.ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red
                    ),
                    modifier = Modifier.size(80.dp)
                ) {
                    Text(
                        text = "STOP",
                        color = Color.White,
                        style = MaterialTheme.typography.body2
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Tap to stop",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
} 