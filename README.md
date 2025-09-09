# Voice Recorder Backend

Backend API for the Voice Recorder Wear OS app, providing secure AI processing, file storage, and user session management.

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

## Deployment

### Railway (Recommended)

1. Connect your GitHub repository to Railway
2. Add environment variables in Railway dashboard
3. Deploy automatically on push

### Render

1. Connect your GitHub repository to Render
2. Use the provided `render.yaml` configuration
3. Add environment variables in Render dashboard

### Docker

```bash
docker build -t voice-recorder-backend .
docker run -p 3000:3000 --env-file .env voice-recorder-backend
```

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

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

MIT License - see LICENSE file for details
