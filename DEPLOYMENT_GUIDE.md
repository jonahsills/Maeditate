# Voice Recorder Backend Deployment Guide

## 🎉 Backend Complete!

Your backend is now ready to deploy! Here's everything you need to know to get it running.

## What We Built

✅ **Complete Backend API** with all endpoints  
✅ **Database Schema** with PostgreSQL  
✅ **S3 File Storage** for audio files  
✅ **JWT Authentication** for secure access  
✅ **AI Integration** with Whisper and Gemini  
✅ **Rate Limiting** and security features  
✅ **Watch App Integration** with fallback support  

## Quick Start (Railway - Recommended)

### 1. Set Up Railway Account
1. Go to [railway.app](https://railway.app)
2. Sign up with GitHub
3. Create a new project

### 2. Deploy Backend
1. **Connect Repository**: Connect your GitHub repo to Railway
2. **Add Database**: Add PostgreSQL service in Railway dashboard
3. **Add Environment Variables** (see below)
4. **Deploy**: Railway will auto-deploy from your `backend/` folder

### 3. Get Your Backend URL
- Railway will give you a URL like: `https://your-app-name.railway.app`
- Copy this URL for your watch app

## Environment Variables

Add these in your hosting platform's environment settings:

### Required Variables
```bash
# Database
DATABASE_URL=postgresql://user:pass@host:port/dbname

# S3 Storage (choose one)
S3_REGION=us-east-1
S3_BUCKET=your-bucket-name
S3_ACCESS_KEY_ID=your-access-key
S3_SECRET_ACCESS_KEY=your-secret-key
PUBLIC_CDN_BASE=https://your-bucket.s3.amazonaws.com

# AI Services
GEMINI_API_KEY=your_gemini_key_here
OPENAI_API_KEY=your_openai_key_here
STT_PROVIDER=whisper

# Security
JWT_SECRET=your_super_secret_jwt_key_here

# CORS (for Base44 portal)
CORS_ORIGIN=https://base44.com,http://localhost:3000

# Server
PORT=3000
NODE_ENV=production
```

### Optional Variables
```bash
# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=120
TRANSCRIPT_RATE_LIMIT_MAX=10

# File Limits
MAX_FILE_SIZE_MB=50
MAX_AUDIO_DURATION_MINUTES=30
```

## S3 Storage Setup

### Option 1: AWS S3
1. Create S3 bucket
2. Get access keys from IAM
3. Set bucket permissions for public read

### Option 2: Cloudflare R2 (Cheaper)
1. Create R2 bucket
2. Get API tokens
3. Use R2's S3-compatible API

## Database Setup

### Railway PostgreSQL
1. Add PostgreSQL service in Railway
2. Copy the `DATABASE_URL` from Railway dashboard
3. The migrations will run automatically on first deploy

### Manual Setup
```bash
# Run migrations manually
npm run migrate
```

## Update Your Watch App

### 1. Update local.properties
```properties
# Replace with your actual backend URL
BACKEND_BASE_URL=https://your-app-name.railway.app
```

### 2. Build and Test
```bash
./gradlew clean assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Testing Your Backend

### 1. Health Check
```bash
curl https://your-backend-url.com/health
```

### 2. Test Authentication
```bash
curl -X POST https://your-backend-url.com/auth/anonymous \
  -H "Content-Type: application/json" \
  -d '{"deviceModel": "Test Watch"}'
```

### 3. Test Upload
```bash
# Get upload URL
curl -X POST https://your-backend-url.com/v1/upload-init \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileExt": "m4a", "contentType": "audio/mp4"}'
```

## Alternative Deployment Options

### Render (Free Tier Available)
1. Connect GitHub repo to Render
2. Use the provided `render.yaml` config
3. Add environment variables
4. Deploy

### Heroku
1. Create Heroku app
2. Add PostgreSQL addon
3. Add environment variables
4. Deploy with Git

### DigitalOcean App Platform
1. Create new app
2. Connect GitHub repo
3. Add database and environment variables
4. Deploy

## Monitoring & Maintenance

### Health Monitoring
- Health check endpoint: `/health`
- Monitor logs in your hosting platform
- Set up alerts for errors

### Cost Management
- Monitor API usage in OpenAI/Gemini dashboards
- Set up billing alerts
- Use rate limiting to prevent abuse

### Database Maintenance
- Regular backups (most platforms do this automatically)
- Monitor connection limits
- Clean up old data periodically

## Troubleshooting

### Common Issues

**1. Database Connection Failed**
- Check `DATABASE_URL` format
- Ensure database is running
- Check network connectivity

**2. S3 Upload Failed**
- Verify S3 credentials
- Check bucket permissions
- Ensure bucket exists

**3. AI Processing Failed**
- Verify API keys are correct
- Check API quotas/limits
- Monitor error logs

**4. Watch App Can't Connect**
- Verify `BACKEND_BASE_URL` is correct
- Check CORS settings
- Test with curl first

### Debug Mode
Set `NODE_ENV=development` for detailed error logs.

## Security Checklist

- ✅ JWT secret is strong and unique
- ✅ API keys are stored securely
- ✅ CORS is configured for your domains only
- ✅ Rate limiting is enabled
- ✅ Database uses SSL in production
- ✅ S3 bucket has proper permissions

## Next Steps

1. **Deploy your backend** using Railway or your preferred platform
2. **Update your watch app** with the backend URL
3. **Test the complete flow** from recording to summary
4. **Set up monitoring** and alerts
5. **Consider adding features** like user accounts, sharing, etc.

## Support

If you run into issues:
1. Check the logs in your hosting platform
2. Test individual endpoints with curl
3. Verify all environment variables are set
4. Check the backend README for more details

Your voice recorder app is now enterprise-ready with a secure, scalable backend! 🚀
