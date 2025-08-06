# Voice Recorder Wear OS

A modern voice recording app for Wear OS with AI processing capabilities, featuring real-time transcription and intelligent summarization.

## Features

### Modern Wear OS UI
- **Material3 Design**: Clean, modern interface using Material3 components
- **Round Screen Optimized**: Layout designed specifically for round Wear OS displays
- **Dynamic Spacing**: Proper padding and spacing for small screens
- **Dark Theme**: Optimized dark theme with proper color contrast

### Recording Interface
- **Centered Mic Button**: Large, easily tappable circular recording button
- **Pulsing Animation**: Visual feedback with pulsing animation during recording
- **Timer Display**: Real-time recording timer (MM:SS format)
- **Waveform Visualization**: Animated waveform bars showing recording activity
- **Haptic Feedback**: Tactile feedback on button interactions

### AI Processing Capabilities
- **Real-time Transcription**: Converts speech to text using OpenAI Whisper API
- **Intelligent Summarization**: Generates AI-powered summaries using Google Gemini
- **Local File Storage**: Saves transcriptions and summaries to device storage
- **Multiple Audio Formats**: Supports WAV, MP3, and other audio formats
- **Fallback Processing**: Graceful handling when AI services are unavailable

### Action Controls
- **Save/Delete Buttons**: Chip-style action buttons for managing recordings
- **Accessibility**: Proper content descriptions for screen readers
- **Visual States**: Clear visual feedback for different app states

### Background Processing
- **Foreground Service**: Continuous recording with persistent notification
- **Background Processing**: AI processing continues even when app is minimized
- **Service Lifecycle Management**: Proper start/stop handling for Wear OS

## Technical Architecture

### Core Components
- **MainActivity**: Jetpack Compose UI with Material3 design
- **VoiceRecorderService**: Background service for audio recording
- **AIProcessingService**: Handles transcription and summarization
- **AudioConverter**: Converts audio formats for AI processing

### Audio Processing
- **MediaRecorder**: High-quality audio recording (AAC format, 16kHz, mono)
- **File Management**: Automatic file naming with timestamps
- **Format Conversion**: On-device audio format conversion for AI APIs

### AI Integration
- **OpenAI Whisper**: Speech-to-text transcription
- **Google Gemini**: AI-powered content summarization
- **API Key Management**: Secure API key handling via BuildConfig
- **Error Handling**: Robust error handling with fallback options

## UI Components

### Main Screen Layout
- `Scaffold` with `TimeText` at the top
- `ScalingLazyColumn` for scrollable content
- Proper content padding for round screens

### Recording Button
- Circular design with pulsing animation
- Mic icon for recording, Stop icon when active
- Large touch target (88dp) for easy interaction
- Color changes based on recording state

### Timer Display
- Bold typography for easy reading
- Color changes to indicate recording state
- Real-time updates during recording

### Waveform Visualization
- 5 animated bars showing recording activity
- Staggered animation timing for realistic effect
- Color changes based on recording state

### Action Buttons
- Chip-style buttons with icons and labels
- Save (blue) and Delete (red) options
- Only visible when recording is complete

## Color Scheme
- **Primary**: Light blue (#64B5F6) for active states
- **Surface**: Dark gray (#121212) for background
- **Error**: Red (#EF5350) for delete actions
- **Surface Variant**: Medium gray (#1E1E1E) for secondary elements

## Permissions
- **RECORD_AUDIO**: Audio recording capability
- **WRITE_EXTERNAL_STORAGE**: Save recordings to device
- **INTERNET**: AI processing and transcription
- **FOREGROUND_SERVICE**: Background recording service
- **VIBRATE**: Haptic feedback

## Dependencies
- `androidx.wear.compose:compose-material:1.2.1`
- `androidx.wear.compose:compose-foundation:1.2.1`
- `androidx.compose.material3:material3:1.1.2`
- `com.google.ai.client.generativeai:generativeai:0.1.1`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `androidx.media:media:1.7.0`

## Setup Requirements

### API Keys
The app requires API keys for AI processing. Add the following to your `local.properties` file:
```
GEMINI_API_KEY=your_gemini_api_key_here
OPENAI_API_KEY=your_openai_api_key_here
```

### Build Requirements
- Android SDK 26+
- Kotlin 1.9.0+
- Jetpack Compose 1.5.4+
- Targets Wear OS devices with round displays

## Building
The app requires Android SDK 26+ and targets Wear OS devices with round displays. Make sure to add your API keys to `local.properties` before building.

## Features in Development
- Cloud storage integration
- Multiple language support
- Advanced audio processing options
- Wear OS companion app integration 