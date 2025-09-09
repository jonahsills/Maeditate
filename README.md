# Voice Recorder Wear OS

A complete voice recording app for Wear OS with AI processing capabilities, featuring real-time transcription and intelligent summarization.

## 🎉 Status: FULLY DEPLOYED & WORKING

✅ **Backend deployed** at `https://maeditate-production.up.railway.app`  
✅ **Database migrated** with all tables created  
✅ **API tested** - authentication, transcription, and summarization working  
✅ **Android app built** and ready for installation  

## 🚀 Quick Start

### Backend (Already Deployed)
- **URL**: `https://maeditate-production.up.railway.app`
- **Health Check**: `GET /health`
- **Database**: PostgreSQL on Railway
- **Storage**: Local file storage (no S3 required)

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

# Create transcript with AI summary
curl -X POST https://maeditate-production.up.railway.app/v1/transcripts \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Idempotency-Key: test-1" \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello from the watch app test.","language":"en","wantSummary":true}'
```

## 📱 What's Working

### Backend API
- ✅ JWT Authentication (7-day tokens)
- ✅ Audio file upload and storage
- ✅ OpenAI Whisper transcription
- ✅ Google Gemini summarization
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
- 📝 **AI Summarization** - Intelligent summaries using Google Gemini
- 📁 **File Storage** - S3-compatible storage for audio files
- 🔄 **Idempotency** - Prevents duplicate processing
- ⚡ **Rate Limiting** - Protects against abuse
- 📊 **Session Management** - Track user sessions and recordings
- 🌐 **CORS Support** - Works with web portals

## Quick Start

### 1. Prerequisites

- Node.js 18+
- PostgreSQL database
- S3-compatible storage (AWS S3, Cloudflare R2, etc.)
- OpenAI API key
- Google Gemini API key

### 2. Installation

```bash
cd backend
npm install
```

### 3. Environment Setup

Copy `env.example` to `.env` and fill in your values:

```bash
cp env.example .env
```

Required environment variables:
- `DATABASE_URL` - PostgreSQL connection string
- `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` - S3 configuration
- `GEMINI_API_KEY` - Google Gemini API key
- `OPENAI_API_KEY` - OpenAI API key
- `JWT_SECRET` - Secret key for JWT signing

### 4. Database Setup

Run migrations to create the database schema:

```bash
npm run migrate
```

### 5. Development

```bash
npm run dev
```

The server will start on `http://localhost:3000`

## API Endpoints

### Authentication
- `POST /auth/anonymous` - Create anonymous user and get JWT token

### File Upload
- `POST /v1/upload-init` - Get presigned URL for audio upload

### Transcription
- `POST /v1/transcripts` - Submit audio for transcription
- `GET /v1/transcripts/:id` - Get transcription status and results

### Sessions
- `GET /v1/sessions` - List user sessions
- `GET /v1/sessions/:id` - Get session details

### Health
- `GET /health` - Health check endpoint

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
BACKEND_BASE_URL=https://maeditate-production.up.railway.app
NODE_ENV=production
```

### Database Schema (Migrated)
- ✅ Users table
- ✅ Devices table  
- ✅ Sessions table
- ✅ Transcripts table
- ✅ Summaries table
- ✅ All indexes and triggers

## Architecture

```
Watch App → Backend API → AI Services
    ↓           ↓            ↓
Offline Queue → Database → File Storage
```

### Components

- **Express Server** - REST API with authentication and rate limiting
- **PostgreSQL** - User sessions, transcripts, and summaries
- **S3 Storage** - Audio file storage with presigned URLs
- **Background Jobs** - Async processing for transcription and summarization
- **JWT Auth** - Secure token-based authentication

### Data Flow

1. **Authentication**: Watch gets JWT token for anonymous user
2. **Upload**: Watch gets presigned URL, uploads audio to S3
3. **Processing**: Backend downloads audio, transcribes with Whisper
4. **Summarization**: Backend generates summary with Gemini
5. **Storage**: Results stored in database, accessible via API

## Security

- JWT tokens expire after 7 days
- Rate limiting prevents abuse
- CORS configured for specific origins
- Input validation with Zod schemas
- Idempotency keys prevent duplicate processing
- API keys stored securely on server

## Monitoring

- Health check endpoint at `/health`
- Structured logging with Morgan
- Error tracking and reporting
- Database connection monitoring

## Development

### Project Structure

```
backend/
├── src/
│   ├── config/          # Database configuration
│   ├── services/        # AI and S3 services
│   ├── types/           # TypeScript type definitions
│   └── server.ts        # Main server file
├── migrations/          # Database migrations
├── scripts/             # Utility scripts
└── package.json
```

### Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build TypeScript to JavaScript
- `npm start` - Start production server
- `npm run migrate` - Run database migrations

## 🎯 Next Steps

### For Testing
1. **Install the app** on your Wear OS device
2. **Record audio** and test the complete flow
3. **Check AI processing** - transcription and summarization

### For Production
1. **Monitor Railway logs** for any issues
2. **Set up alerts** for API errors
3. **Consider S3 storage** for better file management
4. **Add user accounts** if needed

## 📊 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| Backend API | ✅ Deployed | Railway, PostgreSQL, all endpoints working |
| Database | ✅ Migrated | All tables and indexes created |
| Authentication | ✅ Working | JWT tokens, anonymous users |
| AI Processing | ✅ Working | Whisper + Gemini integration |
| Android App | ✅ Built | Ready for installation |
| File Storage | ✅ Working | Local storage on Railway |

## 🏆 Achievement Unlocked

You now have a **fully functional voice recorder app** with:
- ✅ **Production backend** with AI processing
- ✅ **Wear OS app** ready to install
- ✅ **Complete API** with authentication and file handling
- ✅ **Database** with proper schema and migrations
- ✅ **Live testing** confirmed working

## License

MIT License - see LICENSE file for details
