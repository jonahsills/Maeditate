# Voice Recorder Wear OS

A voice recording app for Wear OS that uses OpenAI Whisper for speech-to-text transcription and Google Gemini for AI summaries.

## Features

- **Voice Recording**: Records audio in MP3 format using MediaRecorder
- **Real Speech-to-Text**: Uses OpenAI Whisper API for accurate transcription
- **AI Summaries**: Uses Google Gemini API to generate intelligent summaries
- **Local Storage**: Saves recordings and summaries to device storage
- **Background Processing**: Continues processing even when app is minimized

## Project Structure

```
VoiceRecorderWearOS/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/voicerecorderwearos/
│   │   │   ├── MainActivity.kt          # Main activity with Compose UI
│   │   │   └── VoiceRecorderService.kt  # Background service for recording
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml          # String resources
│   │   │   │   └── colors.xml           # Color definitions
│   │   │   └── xml/
│   │   │       ├── backup_rules.xml     # Backup configuration
│   │   │       └── data_extraction_rules.xml
│   │   └── AndroidManifest.xml          # App manifest with permissions
│   └── build.gradle                     # App-level dependencies
├── build.gradle                         # Project-level configuration
├── settings.gradle                      # Project settings
└── README.md                           # This file
```

## Setup

### 1. API Keys

You need to add your API keys in `app/src/main/java/com/example/voicerecorderwearos/AIProcessingService.kt`:

```kotlin
private val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
private val OPENAI_API_KEY = "YOUR_OPENAI_API_KEY_HERE"
```

- **Gemini API Key**: Get from [Google AI Studio](https://makersuite.google.com/app/apikey)
- **OpenAI API Key**: Get from [OpenAI Platform](https://platform.openai.com/api-keys)

### 2. OpenAI Whisper

The app now uses OpenAI Whisper for speech-to-text transcription:

- **Free Tier**: 3 hours of transcription per month
- **Supports**: MP3, M4A, WAV, and other audio formats
- **No Conversion Needed**: Works directly with MediaRecorder output
- **High Accuracy**: Much better than offline alternatives

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 34 or later
- Wear OS device or emulator for testing

### Building the Project

1. **Clone or download the project**
   ```bash
   git clone <repository-url>
   cd VoiceRecorderWearOS
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the VoiceRecorderWearOS folder and select it

3. **Sync Gradle**
   - Wait for Android Studio to sync the project
   - If prompted, update Gradle wrapper version

4. **Build the Project**
   - Go to Build → Make Project (Ctrl+F9 / Cmd+F9)
   - Ensure there are no compilation errors

### Running on Device

1. **Enable Developer Options on Wear OS Device**
   - Go to Settings → System → About
   - Tap "Build number" 7 times
   - Go back to Settings → Developer options
   - Enable "ADB debugging"

2. **Connect Device**
   - Connect your Wear OS device via USB or Bluetooth
   - Ensure ADB is connected: `adb devices`

3. **Install and Run**
   - Click the "Run" button in Android Studio
   - Select your Wear OS device
   - The app will install and launch

## Usage

1. **Launch the App**
   - Find "Voice Recorder" in your Wear OS app launcher
   - Tap to open the app

2. **Start Recording**
   - Tap the microphone button to start recording
   - The button will turn red and show "Recording..."
   - A notification will appear indicating recording is in progress

3. **Stop Recording**
   - Tap the stop button (red button) to stop recording
   - The recording will be saved automatically
   - The app returns to the ready state

## Permissions

The app requires the following permissions:

- **RECORD_AUDIO**: Required for voice recording
- **WRITE_EXTERNAL_STORAGE**: Required to save recording files
- **READ_EXTERNAL_STORAGE**: Required to access saved recordings
- **WAKE_LOCK**: Required for background recording
- **FOREGROUND_SERVICE**: Required for the recording service

Permissions are requested automatically when needed.

## Technical Details

### Architecture

- **MainActivity**: Handles UI and user interactions using Jetpack Compose
- **VoiceRecorderService**: Background service for audio recording
- **MediaRecorder**: Android's audio recording API
- **Foreground Service**: Ensures recording continues when app is backgrounded

### Key Components

1. **Jetpack Compose UI**
   - Modern declarative UI framework
   - Wear OS optimized components
   - Responsive design for small screens

2. **Permission Handling**
   - Runtime permission requests
   - Graceful handling of permission denials
   - User-friendly permission flow

3. **Background Recording**
   - Foreground service with notification
   - Continues recording when app is minimized
   - Proper resource management

4. **File Management**
   - Timestamped file names
   - External storage for recordings
   - Automatic file organization

## Troubleshooting

### Common Issues

1. **Permission Denied**
   - Go to Settings → Apps → Voice Recorder → Permissions
   - Enable "Microphone" permission

2. **Recording Not Starting**
   - Check if microphone is working in other apps
   - Ensure device is not in silent mode
   - Restart the app

3. **Build Errors**
   - Sync project with Gradle files
   - Clean and rebuild project
   - Update Android Studio if needed

### Debug Information

- Check logcat for detailed error messages
- Look for tags: "VoiceRecorderService" and "MainActivity"
- Recording files are saved in: `/storage/emulated/0/Android/data/com.example.voicerecorderwearos/files/`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly on a Wear OS device
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Create an issue in the repository
- Check the troubleshooting section above
- Ensure you're using a compatible Wear OS device 