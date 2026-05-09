# AI Features

## Overview
Phase 8 adds the core AI layer for the platform. The project remains a backend-integrated AI application rather than a custom model training project.

The AI system is designed for:
- young Mandarin learners aged 7-12
- beginner-friendly explanations
- short and encouraging responses
- teacher-facing draft support
- admin AI usage visibility

## Default AI Provider
The default provider is DeepSeek through an OpenAI-compatible backend integration.

Example backend environment variables:

```env
AI_PROVIDER=deepseek
AI_API_BASE_URL=https://api.deepseek.com
AI_API_KEY=replace-with-your-deepseek-api-key
AI_MODEL_NAME=deepseek-chat
AI_TIMEOUT_SECONDS=30
AI_MAX_OUTPUT_TOKENS=1200
AI_ENABLE_REAL_PROVIDER=false
```

Important:
- Never put the real `AI_API_KEY` in the frontend.
- Never commit the real `AI_API_KEY`.
- Never hard-code the key in Vue components or frontend env files.
- The frontend never receives or stores the DeepSeek key.

## Safe Configuration
Use:
- [../backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/.env.example)
- [../backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered Interactive Chinese Learning Platform for Young Learners/backend/src/main/resources/application-example.yml)

Set the real key locally in the backend environment only.

Example PowerShell session before starting the backend:

```powershell
$env:AI_PROVIDER="deepseek"
$env:AI_API_BASE_URL="https://api.deepseek.com"
$env:AI_API_KEY="your-real-local-key"
$env:AI_MODEL_NAME="deepseek-chat"
$env:AI_ENABLE_REAL_PROVIDER="true"
cd backend
.\mvnw.cmd spring-boot:run
```

## What Happens If No Real Key Is Set
If `AI_ENABLE_REAL_PROVIDER=false`, or the key/base URL/model is missing, the backend does not crash.

Instead:
- AI endpoints return a friendly unavailable response
- the request is still logged safely where practical
- the standard student-facing fallback message is used:

`AI tutor is currently unavailable. Please try again later.`

## Implemented AI Endpoints
Authenticated AI endpoints:
- `POST /api/v1/ai/availability`
- `POST /api/v1/ai/chat`
- `POST /api/v1/ai/vocabulary-helper`
- `POST /api/v1/ai/sentence-practice`
- `POST /api/v1/ai/quiz-generator`
- `POST /api/v1/ai/story-generator`
- `POST /api/v1/ai/learning-recommendation`
- `POST /api/v1/ai/progress-insight`
- `POST /api/v1/ai/teacher-assistant`
- `POST /api/v1/ai/pronunciation-feedback`
- `POST /api/v1/ai/writing-feedback`

Admin AI monitoring:
- `GET /api/v1/admin/ai/usage-logs`

## Implemented Frontend AI Pages
Student:
- AI Tutor
- AI Vocabulary Helper
- AI Sentence Practice
- AI Quiz Generator
- AI Story Generator
- AI Pronunciation Feedback
- AI Writing Feedback

Teacher:
- AI Teacher Assistant
- AI Vocabulary Helper
- AI Quiz Generator
- AI Story Generator

Admin:
- AI Usage Logs

## AI Logs And Records
The backend uses the existing AI tables/entities:
- `ai_chat_sessions`
- `ai_chat_messages`
- `ai_generation_records`
- `ai_feedback_records`
- `ai_usage_logs`

Stored safely where practical:
- user id
- user role
- feature key
- language
- provider name
- model name
- request status
- summarized input/output
- safe error summary
- related entity references when applicable

Not stored:
- full API key
- frontend secrets
- raw provider stack traces in user responses

## Manual Testing
### Unavailable Mode
1. Ensure `AI_ENABLE_REAL_PROVIDER=false` or leave `AI_API_KEY` unset.
2. Start the backend.
3. Open the student AI pages.
4. Submit a tutor message, vocabulary request, sentence request, quiz request, or story request.
5. Confirm the UI shows a friendly unavailable message instead of crashing.
6. Log in as admin and verify `/admin/ai-usage-logs` shows the request status.

### Real DeepSeek Mode
1. Set the real key locally in backend environment variables only.
2. Set `AI_ENABLE_REAL_PROVIDER=true`.
3. Start the backend.
4. Test:
   - AI tutor chat
   - vocabulary helper
   - sentence practice
   - quiz generator
   - story generator
   - teacher assistant
5. Confirm admin AI usage logs show records with provider and model values.

## Prototype Limitations
Phase 8 intentionally does not implement:
- custom AI model training
- professional speech scoring
- professional Mandarin tone scoring
- handwriting recognition
- stroke-order recognition
- AI-generated content auto-publication

Pronunciation feedback and writing feedback are prototype-friendly workflows:
- uploads are accepted and logged
- safe unavailable/prototype responses are returned when the provider does not support audio/image analysis in this phase

## Content Workflow Safety
Teacher/admin AI generation is draft support only.

Important rules:
- generated quiz/story/lesson content is not auto-published
- generated official content must still be reviewed and edited
- Phase 6 content workflow is not bypassed
