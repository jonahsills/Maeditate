# Voice Recorder Wear OS

A complete voice recording app for Wear OS with AI processing capabilities, featuring real-time transcription and intelligent summarization.

## 🎉 Status: FULLY DEPLOYED & WORKING

✅ **Backend deployed** at `https://maeditate-production.up.railway.app`  
✅ **Database migrated** with all tables created  
✅ **S3 storage configured** with private bucket and presigned URLs  
✅ **API tested** - authentication, transcription, and summarization working  
✅ **Android app built** and ready for installation  

## 🚀 Quick Start

### Backend (Already Deployed)
- **URL**: `https://maeditate-production.up.railway.app`
- **Health Check**: `GET /health`
- **Database**: PostgreSQL on Railway
- **Storage**: AWS S3 with private bucket and presigned URLs

### Android App
1. **Install on your Wear OS device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. **App is configured** to connect to the deployed backend automatically

## 🧪 Live API Tests (All Passing)

```bash
# Health check
curl https://maeditate-production.up.railway.app/health

# Get JWT token
curl -X POST https://maeditate-production.up.railway.app/auth/anonymous \
  -H "Content-Type: application/json" \
  -d '{"deviceModel":"Wear OS"}'

# Test complete Option C flow (presigned S3 upload)
./test_option_c.sh
```

## 📱 What's Working

### Backend API
- ✅ JWT Authentication (7-day tokens)
- ✅ **Option C S3 Integration** - Presigned PUT URLs for direct S3 upload
- ✅ **Private S3 Bucket** - Secure audio storage with AWS SDK download
- ✅ OpenAI Whisper transcription
- ✅ **Gemini-first summarization** with OpenAI fallback
- ✅ Session management
- ✅ Rate limiting and security
- ✅ Health monitoring

### Wear OS App
- ✅ Material3 design for round screens
- ✅ Voice recording with haptic feedback
- ✅ Real-time timer and waveform visualization
- ✅ Background processing service
- ✅ Offline queue with sync
- ✅ AI processing integration
- ✅ Local file storage

## Features

- 🔐 **JWT Authentication** - Anonymous user creation with 7-day tokens
- 🎤 **Audio Processing** - Server-side transcription using OpenAI Whisper
- 📝 **AI Summarization** - Gemini-first with OpenAI fallback
- 📁 **S3 Storage** - Private bucket with presigned URLs (Option C)
- 🔄 **Idempotency** - Prevents duplicate processing
- ⚡ **Rate Limiting** - Protects against abuse
- 📊 **Session Management** - Track user sessions and recordings
- 🌐 **CORS Support** - Works with web portals

## 🏗️ Architecture

### Option C: Presigned S3 Upload Flow
```
Watch App → Backend API → S3 Storage
    ↓           ↓            ↓
Get Token → Upload Init → Presigned URL
    ↓           ↓            ↓
Upload Audio → Direct S3 → Private Bucket
    ↓           ↓            ↓
Create Job → Backend Download → Process
```

### Components

- **Express Server** - REST API with authentication and rate limiting
- **PostgreSQL** - User sessions, transcripts, and summaries
- **AWS S3** - Private bucket with presigned URLs for secure uploads
- **Background Jobs** - Async processing for transcription and summarization
- **JWT Auth** - Secure token-based authentication

### Data Flow

1. **Authentication**: Watch gets JWT token for anonymous user
2. **Upload Init**: Watch requests presigned S3 URL from backend
3. **Direct Upload**: Watch uploads audio directly to S3 (bypassing backend)
4. **Processing**: Backend downloads from private S3, transcribes with Whisper
5. **Summarization**: Backend generates summary with Gemini (OpenAI fallback)
6. **Storage**: Results stored in database, accessible via API

## 🛠️ Deployment (Already Complete)

### Railway Deployment ✅
- **Service**: `https://maeditate-production.up.railway.app`
- **Database**: PostgreSQL on Railway
- **Environment**: Production with all variables configured
- **Auto-deploy**: Enabled on git push

### Environment Variables (Configured)
```bash
DATABASE_URL=postgresql://... (Railway managed)
JWT_SECRET=configured
GEMINI_API_KEY=configured
OPENAI_API_KEY=configured
S3_REGION=us-east-2
S3_BUCKET=maeditate-audio
S3_ACCESS_KEY_ID=configured
S3_SECRET_ACCESS_KEY=configured
PUBLIC_CDN_BASE=https://maeditate-audio.s3.us-east-2.amazonaws.com
NODE_ENV=production
```

### S3 Configuration ✅
- **Bucket**: `maeditate-audio` (private)
- **Region**: `us-east-2`
- **Access**: IAM user with PutObject/GetObject permissions
- **Security**: Block public access enabled
- **Flow**: Presigned PUT URLs for direct client upload

### Database Schema (Migrated)
- ✅ Users table
- ✅ Devices table  
- ✅ Sessions table
- ✅ Transcripts table
- ✅ Summaries table
- ✅ All indexes and triggers

## API Endpoints

### Authentication
- `POST /auth/anonymous` - Create anonymous user and get JWT token

### File Upload (Option C)
- `POST /v1/upload-init` - Get presigned S3 URL for direct upload
- Returns: `{ sessionId, uploadUrl, audioUrl }`

### Transcription
- `POST /v1/transcripts` - Submit audio URL for transcription
- `GET /v1/transcripts/:id` - Get transcription status and results

### Sessions
- `GET /v1/sessions` - List user sessions
- `GET /v1/sessions/:id` - Get session details

### Health
- `GET /health` - Health check endpoint

## Security

- JWT tokens expire after 7 days
- Rate limiting prevents abuse
- CORS configured for specific origins
- Input validation with Zod schemas
- Idempotency keys prevent duplicate processing
- API keys stored securely on server
- **Private S3 bucket** with IAM-based access control

## AI Processing

### Transcription
- **Provider**: OpenAI Whisper (`whisper-1`)
- **Input**: Audio files (WAV, MP3, M4A, etc.)
- **Output**: Text with language detection and confidence scores

### Summarization
- **Primary**: Google Gemini (`gemini-1.5-flash`)
- **Fallback**: OpenAI GPT-4o-mini
- **Output**: Concise meditation session summaries (≤80 words)

## Monitoring

- Health check endpoint at `/health`
- Structured logging with Morgan
- Error tracking and reporting
- Database connection monitoring
- S3 upload/download monitoring

## Development

### Project Structure

```
VoiceRecorderWearOS/
├── app/                    # Wear OS Android app
│   ├── src/main/java/      # Kotlin source code
│   └── build.gradle       # Android build configuration
├── backend/                # Node.js backend
│   ├── src/
│   │   ├── config/        # Database configuration
│   │   ├── services/       # AI and S3 services
│   │   ├── types/          # TypeScript type definitions
│   │   └── server.ts       # Main server file
│   ├── migrations/         # Database migrations
│   └── package.json
├── test_option_c.sh        # End-to-end test script
└── README.md
```

### Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build TypeScript to JavaScript
- `npm start` - Start production server
- `npm run migrate` - Run database migrations
- `./test_option_c.sh` - Test complete Option C flow

## 🎯 Next Steps

### For Testing
1. **Install the app** on your Wear OS device
2. **Record audio** and test the complete flow
3. **Check AI processing** - transcription and summarization

### For Production
1. **Monitor Railway logs** for any issues
2. **Set up S3 lifecycle rules** to auto-delete old audio files
3. **Monitor API usage** and costs
4. **Consider adding user accounts** if needed

## 📊 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| Backend API | ✅ Deployed | Railway, PostgreSQL, all endpoints working |
| Database | ✅ Migrated | All tables and indexes created |
| S3 Storage | ✅ Configured | Private bucket with presigned URLs |
| Authentication | ✅ Working | JWT tokens, anonymous users |
| AI Processing | ✅ Working | Whisper + Gemini/OpenAI integration |
| Android App | ✅ Built | Ready for installation |
| Option C Flow | ✅ Tested | End-to-end working |

## 🏆 Achievement Unlocked

You now have a **fully functional voice recorder app** with:
- ✅ **Production backend** with AI processing
- ✅ **Wear OS app** ready to install
- ✅ **Complete API** with authentication and file handling
- ✅ **Database** with proper schema and migrations
- ✅ **S3 integration** with private bucket and presigned URLs
- ✅ **Live testing** confirmed working

## License

MIT License - see LICENSE file for details