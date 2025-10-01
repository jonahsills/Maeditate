#!/bin/bash

# Voice Recorder - Option C (Presigned PUT) Test Script
# Make sure to update BASE_URL with your actual Railway backend URL

# Configuration
BASE_URL="https://maeditate-production.up.railway.app"
TEST_AUDIO_FILE="sample.wav"

echo "🚀 Testing Voice Recorder Backend - Option C Flow"
echo "================================================"

# Check if test audio file exists
if [ ! -f "$TEST_AUDIO_FILE" ]; then
    echo "❌ Error: Test audio file '$TEST_AUDIO_FILE' not found!"
    echo "Please make sure you have a test audio file in the project root."
    exit 1
fi

echo "📁 Using test audio file: $TEST_AUDIO_FILE"

# Step 1: Get authentication token
echo ""
echo "🔐 Step 1: Getting authentication token..."
TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/anonymous" \
  -H "Content-Type: application/json" \
  -d '{"deviceModel":"Test Watch"}')

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Error: Failed to get authentication token"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "✅ Got token: ${TOKEN:0:20}..."

# Step 2: Get presigned upload URL
echo ""
echo "📤 Step 2: Getting presigned upload URL..."
UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/upload-init" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fileExt":"wav","contentType":"audio/wav"}')

UPLOAD_URL=$(echo "$UPLOAD_RESPONSE" | jq -r '.uploadUrl')
AUDIO_URL=$(echo "$UPLOAD_RESPONSE" | jq -r '.audioUrl')

if [ "$UPLOAD_URL" = "null" ] || [ -z "$UPLOAD_URL" ]; then
    echo "❌ Error: Failed to get upload URL"
    echo "Response: $UPLOAD_RESPONSE"
    exit 1
fi

echo "✅ Got upload URL: ${UPLOAD_URL:0:50}..."
echo "✅ Audio URL will be: ${AUDIO_URL:0:50}..."

# Step 3: Upload file directly to S3
echo ""
echo "☁️ Step 3: Uploading file to S3..."
UPLOAD_RESULT=$(curl -s -X PUT "$UPLOAD_URL" \
  -H "Content-Type: audio/wav" \
  --upload-file "$TEST_AUDIO_FILE")

if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to upload file to S3"
    exit 1
fi

echo "✅ Successfully uploaded file to S3!"

# Step 4: Create transcript job
echo ""
echo "📝 Step 4: Creating transcript job..."
TRANSCRIPT_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/transcripts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"audioUrl\":\"$AUDIO_URL\",\"wantSummary\":true}")

TRANSCRIPT_ID=$(echo "$TRANSCRIPT_RESPONSE" | jq -r '.transcriptId')

if [ "$TRANSCRIPT_ID" = "null" ] || [ -z "$TRANSCRIPT_ID" ]; then
    echo "❌ Error: Failed to create transcript job"
    echo "Response: $TRANSCRIPT_RESPONSE"
    exit 1
fi

echo "✅ Created transcript job: $TRANSCRIPT_ID"

# Step 5: Poll for completion
echo ""
echo "⏳ Step 5: Polling for transcript completion..."
echo "This may take several minutes for audio processing..."

MAX_POLLS=60
POLL_COUNT=0

while [ $POLL_COUNT -lt $MAX_POLLS ]; do
    POLL_COUNT=$((POLL_COUNT + 1))

    STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/v1/transcripts/$TRANSCRIPT_ID" \
      -H "Authorization: Bearer $TOKEN")

    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')

    echo "   Poll $POLL_COUNT: Status = $STATUS"

    case $STATUS in
        "COMPLETE")
            echo ""
            echo "🎉 SUCCESS! Transcript completed!"
            echo ""
            echo "📝 Transcript Details:"
            echo "$STATUS_RESPONSE" | jq '.'
            echo ""
            echo "✅ Option C flow test completed successfully!"
            exit 0
            ;;
        "FAILED")
            echo ""
            echo "❌ Transcript failed:"
            echo "$STATUS_RESPONSE" | jq '.'
            exit 1
            ;;
        "PENDING"|"TRANSCRIBING"|"SUMMARIZING")
            sleep 5
            ;;
        *)
            echo "Unknown status: $STATUS"
            sleep 5
            ;;
    esac
done

echo ""
echo "⏰ Timeout: Transcript took too long to complete"
echo "You can check the status manually with:"
echo "curl -X GET \"$BASE_URL/v1/transcripts/$TRANSCRIPT_ID\" -H \"Authorization: Bearer $TOKEN\""
exit 1
