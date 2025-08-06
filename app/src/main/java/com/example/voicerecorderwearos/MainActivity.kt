package com.example.voicerecorderwearos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private val isRecordingState = mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, can start recording
            Log.d("MainActivity", "Audio permission granted")
        } else {
            Log.d("MainActivity", "Audio permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceRecorderWearOSTheme {
                VoiceRecorderScreen(
                    isRecording = isRecordingState.value,
                    onStartRecording = { 
                        isRecordingState.value = true
                        startVoiceRecording() 
                    },
                    onStopRecording = { 
                        isRecordingState.value = false
                        stopVoiceRecording() 
                    },
                    onSaveRecording = { saveRecording() },
                    onDeleteRecording = { deleteRecording() }
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
                try {
                    Log.d("MainActivity", "Starting voice recording service...")
                    val intent = Intent(this, VoiceRecorderService::class.java).apply {
                        action = "START_RECORDING"
                    }
                    startForegroundService(intent)
                    Log.d("MainActivity", "Voice recording service started successfully")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start voice recording service", e)
                    // Show user-friendly error message
                    // You could add a Toast here if needed
                }
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
        try {
            Log.d("MainActivity", "Stopping voice recording service...")
            val intent = Intent(this, VoiceRecorderService::class.java).apply {
                action = "STOP_RECORDING"
            }
            startService(intent)
            Log.d("MainActivity", "Voice recording service stop requested")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to stop voice recording service", e)
        }
    }
    
    private fun saveRecording() {
        Log.d("MainActivity", "Save recording")
        // Send intent to service to save and process
        val intent = Intent(this, VoiceRecorderService::class.java).apply {
            action = "SAVE_RECORDING"
        }
        startService(intent)
    }
    
    private fun deleteRecording() {
        Log.d("MainActivity", "Delete recording")
        // Send intent to service to delete
        val intent = Intent(this, VoiceRecorderService::class.java).apply {
            action = "DELETE_RECORDING"
        }
        startService(intent)
    }
}

@Composable
fun VoiceRecorderWearOSTheme(content: @Composable () -> Unit) {
    androidx.wear.compose.material.MaterialTheme(
        colors = androidx.wear.compose.material.MaterialTheme.colors.copy(
            primary = Color(0xFF64B5F6),
            primaryVariant = Color(0xFF1976D2),
            secondary = Color(0xFFEF5350),
            secondaryVariant = Color(0xFFD32F2F),
            surface = Color(0xFF121212),
            onSurface = Color.White,
            onPrimary = Color.Black,
            onSecondary = Color.White
        ),
        content = content
    )
}

@Composable
fun VoiceRecorderScreen(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onDeleteRecording: () -> Unit
) {
    var recordingTime by remember { mutableStateOf(0L) }
    var hasRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    
    // Timer effect
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingTime += 1000
        }
    }
    
    // Reset timer when recording stops
    LaunchedEffect(isRecording) {
        if (!isRecording && recordingTime > 0) {
            hasRecording = true
        }
    }
    
    Scaffold(
        timeText = {
            TimeText(
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            autoCentering = AutoCenteringParams(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            
            // Timer
            item {
                RecordingTimer(
                    isRecording = isRecording,
                    recordingTime = recordingTime
                )
            }
            
            // Main recording button
            item {
                RecordingButton(
                    isRecording = isRecording,
                    onRecordingToggle = {
                        // Haptic feedback
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                        
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                            recordingTime = 0L
                        }
                    }
                )
            }
            
            // Waveform visualization (only when recording)
            if (isRecording) {
                item {
                    WaveformVisualization(isRecording = true)
                }
            }
            
            // Action buttons (only show when not recording and has recording)
            item {
                if (!isRecording && hasRecording) {
                    ActionButtons(
                        onSave = {
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                            onSaveRecording()
                            hasRecording = false
                        },
                        onDelete = {
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                            onDeleteRecording()
                            hasRecording = false
                        }
                    )
                }
            }
            
        }
    }
}

@Composable
fun RecordingTimer(
    isRecording: Boolean,
    recordingTime: Long
) {
    val formattedTime = remember(recordingTime) {
        val minutes = (recordingTime / 60000).toInt()
        val seconds = ((recordingTime % 60000) / 1000).toInt()
        String.format("%02d:%02d", minutes, seconds)
    }
    
    Text(
        text = if (isRecording) formattedTime else "00:00",
        style = androidx.wear.compose.material.MaterialTheme.typography.title3.copy(
            fontWeight = FontWeight.Bold
        ),
        color = if (isRecording) androidx.wear.compose.material.MaterialTheme.colors.primary 
                else androidx.wear.compose.material.MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

@Composable
fun RecordingButton(
    isRecording: Boolean,
    onRecordingToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(88.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isRecording) androidx.wear.compose.material.MaterialTheme.colors.secondary
                else androidx.wear.compose.material.MaterialTheme.colors.primary
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onRecordingToggle,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            colors = androidx.wear.compose.material.ButtonDefaults.buttonColors(
                backgroundColor = Color.Transparent
            )
        ) {
            androidx.wear.compose.material.Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
fun WaveformVisualization(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bars = 5
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(bars) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = if (isRecording) 24f else 8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 100,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_height_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isRecording) androidx.wear.compose.material.MaterialTheme.colors.primary
                        else androidx.wear.compose.material.MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
fun ActionButtons(
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Save button
        Chip(
            onClick = onSave,
            label = {
                Text(
                    text = "Save",
                    style = androidx.wear.compose.material.MaterialTheme.typography.body2
                )
            },
            icon = {
                androidx.wear.compose.material.Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save recording",
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = ChipDefaults.chipColors(
                backgroundColor = androidx.wear.compose.material.MaterialTheme.colors.primary,
                contentColor = androidx.wear.compose.material.MaterialTheme.colors.onPrimary
            ),
            modifier = Modifier.height(28.dp)
        )
        
        // Delete button
        Chip(
            onClick = onDelete,
            label = {
                Text(
                    text = "Delete",
                    style = androidx.wear.compose.material.MaterialTheme.typography.body2
                )
            },
            icon = {
                androidx.wear.compose.material.Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete recording",
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = ChipDefaults.chipColors(
                backgroundColor = androidx.wear.compose.material.MaterialTheme.colors.secondary,
                contentColor = androidx.wear.compose.material.MaterialTheme.colors.onSecondary
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Preview
@Composable
fun VoiceRecorderPreview() {
    VoiceRecorderWearOSTheme {
        VoiceRecorderScreen(
            isRecording = false,
            onStartRecording = { /* Preview only */ },
            onStopRecording = { /* Preview only */ },
            onSaveRecording = { /* Preview only */ },
            onDeleteRecording = { /* Preview only */ }
        )
    }
}

@Preview(name = "Recording State")
@Composable
fun VoiceRecorderRecordingPreview() {
    VoiceRecorderWearOSTheme {
        var isRecording by remember { mutableStateOf(true) }
        
        Scaffold(
            timeText = {
                            TimeText(
                modifier = Modifier.padding(top = 8.dp)
            )
            }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                item {
                    RecordingTimer(
                        isRecording = isRecording,
                        recordingTime = 45000L
                    )
                }
                
                item {
                    RecordingButton(
                        isRecording = isRecording,
                        onRecordingToggle = { isRecording = !isRecording }
                    )
                }
                
                item {
                    WaveformVisualization(isRecording = isRecording)
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
} 