# Setup Guide

## Prerequisites
- Node.js 26.x or another modern Node 20+ runtime that works with the frontend toolchain
- npm 11.x or compatible
- Java 21 LTS
- Docker Desktop with Docker Compose
- A Windows shell or terminal capable of running the provided commands

Maven does not need to be installed globally because the backend uses `mvnw.cmd`.

## Project Structure
- [frontend](../frontend)
- [backend](../backend)
- [docs](../docs)

## 1. Start MySQL
From the project root:

```powershell
docker compose up -d
```

Default local database values:
- Host: `localhost`
- Port: `3307`
- Database: `ai_chinese_learning`
- Username: `root`
- Password: `root`

Optional validation:

```powershell
docker compose config
```

## 2. Configure Backend Environment Variables
Use placeholders only. Reference files:
- [backend/.env.example](../backend/.env.example)
- [backend/src/main/resources/application-example.yml](../backend/src/main/resources/application-example.yml)

Important groups:
- Database: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- Security: `APP_CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `JWT_EXPIRATION`
- Default admin: `DEFAULT_ADMIN_EMAIL`, `DEFAULT_ADMIN_PASSWORD`
- Uploads: `UPLOAD_DIR`, `MAX_FILE_SIZE`, `RECORDED_VIDEO_UPLOAD_DIR`, `MAX_VIDEO_FILE_SIZE`
- AI: `AI_PROVIDER`, `AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL_NAME`, `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`, `AI_ENABLE_REAL_PROVIDER`
- Payment: `PAYMENT_PROVIDER`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLIC_KEY`, `STRIPE_WEBHOOK_SECRET`, `PAYMENT_SUCCESS_URL`, `PAYMENT_CANCEL_URL`, `PAYMENT_CURRENCY`, `PAYMENT_ENABLE_REAL_PROVIDER`, `PAYMENT_TIMEOUT_SECONDS`, `PAYMENT_MOCK_SUCCESS_ENABLED`
- SMTP: `SMTP_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`

Example placeholders:

```env
DB_HOST=localhost
DB_PORT=3307
DB_NAME=ai_chinese_learning
DB_USERNAME=root
DB_PASSWORD=root
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
JWT_SECRET=replace-with-a-strong-plain-text-secret-at-least-32-characters-long
JWT_EXPIRATION=3600000
DEFAULT_ADMIN_EMAIL=admin@example.com
DEFAULT_ADMIN_PASSWORD=SuperSecure123!
UPLOAD_DIR=./uploads
MAX_FILE_SIZE=5MB
RECORDED_VIDEO_UPLOAD_DIR=./uploads/recorded-videos
MAX_VIDEO_FILE_SIZE=100MB
AI_PROVIDER=deepseek
AI_API_BASE_URL=https://api.deepseek.com
AI_API_KEY=replace-with-your-deepseek-api-key
AI_MODEL_NAME=deepseek-chat
AI_TIMEOUT_SECONDS=30
AI_MAX_OUTPUT_TOKENS=1200
AI_ENABLE_REAL_PROVIDER=false
PAYMENT_PROVIDER=stripe
STRIPE_SECRET_KEY=replace-with-your-stripe-test-secret-key
STRIPE_PUBLIC_KEY=replace-with-your-stripe-test-public-key
STRIPE_WEBHOOK_SECRET=replace-with-your-stripe-webhook-secret
PAYMENT_SUCCESS_URL=http://localhost:5173/payment/success
PAYMENT_CANCEL_URL=http://localhost:5173/payment/cancel
PAYMENT_CURRENCY=MYR
PAYMENT_ENABLE_REAL_PROVIDER=false
PAYMENT_TIMEOUT_SECONDS=30
PAYMENT_MOCK_SUCCESS_ENABLED=true
SMTP_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=
```

Important notes:
- Never commit real secrets
- Never put backend secrets into frontend files
- SMTP is optional
- AI and payment providers must fail gracefully when unavailable

## 3. Run the Backend
From [backend](../backend):

```powershell
.\mvnw.cmd spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/v1/health
```

## 4. Run the Frontend
From [frontend](../frontend):

```powershell
npm install
npm run dev
```

Frontend URL:
- `http://localhost:5173`

## 5. Frontend and Backend Verification Commands
Frontend:

```powershell
npm run build
npm run test
```

Backend:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

Infrastructure:

```powershell
docker compose config
```

## 6. Common Troubleshooting
- Backend fails at startup:
  - confirm MySQL is running on `localhost:3307`
  - confirm `JWT_SECRET` is set and long enough
  - confirm `DB_*` values match the local Docker setup
- Frontend cannot call the API:
  - confirm backend is on port `8080`
  - confirm `APP_CORS_ALLOWED_ORIGINS` includes `http://localhost:5173`
- AI pages show unavailable messages:
  - this is acceptable if `AI_ENABLE_REAL_PROVIDER=false` or the key is not configured
- Stripe checkout is unavailable:
  - this is acceptable in test mode or when real provider mode is disabled
- Email is not sent:
  - this is acceptable when SMTP is disabled; in-app notifications should still work
- Charts show empty states:
  - this is acceptable when the underlying database has limited demo data

## 7. Safe Demo Setup Notes
- Do not commit `frontend/dist`
- Do not commit `backend/target`
- Do not commit uploads, logs, temp files, or local env files
- Do not expose SMTP, AI, Stripe, JWT, or database secrets
- Demo fallback behaviour should be explained honestly rather than hidden
