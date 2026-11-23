# MandarinSpark

A desktop web app for beginners learning Mandarin. There are two roles: **learners** and **admins** (the MandarinSpark
team). Learners get a learning path with XP, ranks, streaks and badges, a level check and a personal learning plan, a
practice library (flashcards, listening, tracing, drag & drop, matching, typing and a mistakes review), an AI helper
(talk partner, Ask SparkBot, quick quiz and story generators, pronunciation and handwriting checks), themed
classes made by the MandarinSpark team, and notifications. Admins create and publish the classes and lessons, run the
learning path and practice library, answer support requests (from the Help page and from learners' "Report a problem"
dialog; replies go by e-mail), review AI reports and settings, manage users and read analytics and the audit log. The
interface is available in English, Bahasa Melayu and Simplified Chinese, and every e-mail is written in its reader's
language: an account's preferred language (set at sign-up and kept current by the language switch), the inviting
admin's language for an admin invite, and for support's reply to a visitor the language of the Help page they wrote
from; English when none is known.

| Part       | Stack                                                                                             |
| ---------- | ------------------------------------------------------------------------------------------------- |
| `backend`  | Java 21, Spring Boot 3.5, Spring Security (JWT access token + rotating HttpOnly refresh cookie), JPA, Flyway, MySQL 8 |
| `frontend` | Vue 3, TypeScript, Vite, Pinia, vue-router, vue-i18n; Vitest unit tests, Playwright end-to-end tests |

The frontend calls the REST API under `/api/v1`; in development Vite proxies `/api/v1` to the backend.

## Prerequisites

- JDK 21 (the build enforces `[21, 22)`); Maven comes with the wrapper (`backend/mvnw`, `backend/mvnw.cmd`).
- Node.js 22 (`frontend/package.json` engines: `>=22 <23`).
- MySQL 8 with `utf8mb4`. Every profile connects to `DB_HOST` (default `localhost`) and `DB_PORT` (default **3307**).

## Backend profiles

| Profile               | Database                                                                      | Schema                          | Use |
| --------------------- | ----------------------------------------------------------------------------- | ------------------------------- | --- |
| *(none)*              | `DB_NAME` (default `ai_chinese_learning`), credentials from `DB_USERNAME`/`DB_PASSWORD` | Flyway migrations, Hibernate `validate` | base settings for every environment |
| `local`               | as above, user defaults to `aichinese_app`                                    | Flyway, `validate`              | development against a persistent database; binds to 127.0.0.1 and has a development JWT secret |
| `demo`                | `DEMO_DB_NAME` (default `ai_chinese_learning_demo`, created if missing)       | **rebuilt on every start**: its tables are dropped, Hibernate creates them, then demo data is filled in | demonstrations and end-to-end tests; AI provider and e-mail off |
| `prod` / `production` | as the base profile                                                           | Flyway, `validate`              | deployment; start-up guards refuse unsafe settings (see below) |

`mvnw spring-boot:run` uses the `local` profile unless `-Dspring-boot.run.profiles=...` says otherwise.

## Running locally

Create the database and an application user once (any names work; these match the defaults):

```sql
CREATE DATABASE ai_chinese_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'aichinese_app'@'localhost' IDENTIFIED BY 'a-strong-password';
GRANT ALL PRIVILEGES ON ai_chinese_learning.* TO 'aichinese_app'@'localhost';
```

Backend (port 8080; Flyway creates the tables on first start):

```bash
cd backend
DB_PASSWORD=a-strong-password DEFAULT_ADMIN_EMAIL=admin@example.com DEFAULT_ADMIN_PASSWORD='Choose-A-Strong-1' ./mvnw spring-boot:run
```

On Windows, the packaged application also runs from directories with Chinese characters or spaces. From
`backend`, with your database credentials set in the environment, run these PowerShell commands:

```powershell
.\mvnw.cmd -DskipTests package
java -jar target/mandarinspark.jar --spring.profiles.active=local
```

Rebuild the jar after editing backend code. Use `--spring.profiles.active=demo` for the demo database.

Frontend (port 5174):

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5174. The default admin is created on start-up only when `DEFAULT_ADMIN_EMAIL` and
`DEFAULT_ADMIN_PASSWORD` are both set and no account with that e-mail exists yet.

### Local OpenAI key

Copy `backend/config/application-local.yml.example` to `backend/config/application-local.yml` (ignored by git). When
running with the `local` profile from `backend`, that file optionally imports `ai-local.properties` from
`%LOCALAPPDATA%\MandarinSpark\credentials\` on Windows (or `~/.config/MandarinSpark/credentials/` on macOS/Linux). To
read the key from a private file instead of setting `AI_API_KEY`, configure `ai-local.properties`:

```properties
app.ai.enable-real-provider=true
app.ai.api-key-file=/path/to/openai-api-key.txt
```

Save your OpenAI API key as the only line of that file. SparkBot, Xiaomei text chat, quiz and story generation, admin
quiz drafts and handwriting checks use this key. The running backend reads `app.ai.api-key-file` on each request. Reload
an open AI Helper page after saving it. An explicitly configured `AI_API_KEY` takes precedence over the file.

### Live video with Xiaomei

In [Tavus PAL Maker](https://maker.tavus.io/), create a PAL with a stock face and set its language to Chinese (`zh`).
Publish an [Embed deployment](https://docs.tavus.io/sections/deployments/embed) and add the exact origin used to open
the app, such as `http://127.0.0.1:5174`, to its allowed origins. Copy `frontend/.env.local.example` to
`frontend/.env.local` (ignored by git) and set your public deployment ID:

```dotenv
VITE_TAVUS_DEPLOYMENT_ID=your_deployment_id
```

Restart Vite, or rebuild for deployment. With the ID configured, the microphone in **Talk with Xiaomei** starts a Tavus
call in the avatar area. The call uses the selected scenario and updates the transcript on the right. Live calls use the
Tavus account's usage quota.

### Pronunciation and handwriting

- **Pronunciation.** Enable [Tencent Cloud Smart Oral Evaluation (New Age)](https://console.cloud.tencent.com/soenew).
  Set `TENCENT_SOE_SECRET_ID` and `TENCENT_SOE_SECRET_KEY` from [CAM API key management](https://console.cloud.tencent.com/cam/capi)
  and set `TENCENT_SOE_APP_ID` to the numeric AppID on [Tencent Cloud Account Information](https://console.cloud.tencent.com/developer).
  For a local setup, copy `backend/config/application-local.yml.example` to `backend/config/application-local.yml` and
  set `secret-id`, `secret-key` and `soe-app-id` under `app.ai.pronunciation`. Start the backend from `backend` with
  the local profile to load it.
  Install [FFmpeg](https://ffmpeg.org/download.html) on the backend machine. Keep it on `PATH` or set `AI_FFMPEG_PATH` to the
  executable. The backend decodes WebM, WAV and MP3 to 16 kHz, 16-bit mono PCM. Short clips use the service's
  [recorded-audio mode](https://cloud.tencent.com/document/product/1774/107497) with a single upload and sentence-level
  tone detection. Long targets use paragraph assessment. Clips over a minute are sent at playback speed, up to five minutes.
- **Handwriting.** Set `AI_PROVIDER=openai` and configure `AI_API_KEY` and an image-capable `AI_MODEL_NAME`. When a photo
  is readable, the model grades visible character correctness, strokes and proportions and suggests one change.
  When the photo is unclear, the student can retake it before a score is recorded.

Set `AI_ENABLE_REAL_PROVIDER=true` and restart the backend for either check. Keep all credentials on the backend.
The local profile can also read these settings from the private `backend/config/application-local.yml`.
If that file sets `app.ai.api-key` or `app.ai.enable-real-provider` directly, its value replaces the placeholder
in `application.yml`. To keep the environment override, use a placeholder such as
`enable-real-provider: ${AI_ENABLE_REAL_PROVIDER:false}` in the local file too. Files under
`src/main/resources` are packaged into the jar. Keep credentials outside that directory.
The endpoint `POST /api/v1/ai/practice-checks` takes the target and one file. Recent checks store the result without
keeping the audio or photo. Older recording scores retain their original interpretation.

### Demo data

```bash
cd backend
DB_USERNAME=root DB_PASSWORD=... DEFAULT_ADMIN_EMAIL=admin@fyp.local DEFAULT_ADMIN_PASSWORD=... \
DEMO_STUDENT_PASSWORD=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The demo database is emptied and re-seeded on every start (only `DEMO_DB_NAME`; the start-up refuses any other
database). Four learners share `DEMO_STUDENT_PASSWORD` — `student@fyp.local` (Amira Hassan),
`chen.hao@fyp.local`, `zhang.yi@fyp.local` and `nur.aisyah@fyp.local`. The deactivated learner
`zhang.yuxi@fyp.local` cannot sign in. Use `DEFAULT_ADMIN_EMAIL` and `DEFAULT_ADMIN_PASSWORD` for the admin.
The catalogue has eight published classes, one archived class (*Mandarin Foundations*, finished by Amira before it was
archived), and two admin drafts. Demo lesson video links open a YouTube search for the lesson topic. Without
`DEFAULT_ADMIN_EMAIL` and `DEFAULT_ADMIN_PASSWORD` there is no admin, so demo support requests, admin notifications
and the audit log are skipped (learner notifications are still seeded).

## Configuration reference

The backend reads environment variables through Spring properties in `application.yml` and its profile-specific files.
For example, `JWT_SECRET` sets `app.security.jwt.secret` and `SMTP_HOST` sets `spring.mail.host`. These properties can
also be set directly. Vite reads the `VITE_` variables when the dev server starts or the frontend is built.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3307`, `ai_chinese_learning` | MySQL server and database |
| `DB_USERNAME`, `DB_PASSWORD` | empty (`local`: `aichinese_app` / placeholder) | database credentials |
| `DB_SSL_MODE`, `DB_ALLOW_PUBLIC_KEY_RETRIEVAL` | `DISABLED` (`prod`: `REQUIRED`), `true` (`prod`: `false`) | MySQL transport encryption and key retrieval (`prod` refuses anything weaker than `REQUIRED`, `VERIFY_CA` or `VERIFY_IDENTITY`) |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` (hardcoded `false` in `prod`) | baseline an existing non-empty schema on first Flyway run outside production |
| `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE` | `30`, `2` | connection pool |
| `DEMO_DB_NAME` | `ai_chinese_learning_demo` | database of the `demo` profile |
| `JWT_SECRET` | none (`local`/`demo` have development values) | HMAC key for access tokens and the run ID and start time used by per-set hint limits. Changing it signs everyone out and makes open lessons/sets ask to be reopened before the next hint |
| `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | `900000` (15 min), `604800000` (7 days) | token lifetimes in ms |
| `JWT_REFRESH_COOKIE_NAME`, `JWT_REFRESH_COOKIE_SECURE`, `JWT_REFRESH_COOKIE_SAME_SITE` | `mandarin_spark_refresh`, `false`, `Strict` | refresh-token cookie |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5174,http://127.0.0.1:5174` outside production | comma-separated exact origins allowed to call the API with credentials |
| `APP_SECURITY_FORWARDED_TRUSTED_PROXIES` | empty | proxy IPs/CIDRs whose `X-Forwarded-For` is trusted (loopback always is); the resulting client address feeds the per-IP rate limits and is stored with each refresh token |
| `REGISTER_REQUESTS_PER_WINDOW`, `FORGOT_PASSWORD_REQUESTS_PER_WINDOW` | `100`, `10` | requests per 15-minute window: sign-ups per client IP; forgot-password requests per IP and per e-mail address. The first limit also caps sign-up confirmation links per IP, and sign-in attempts and session refreshes per IP at three times its value (every page load and every renewal of the 15-minute access token counts, so raise it when many learners share one address). The second limit also caps reset-password submissions (per IP), e-mail change links (per IP and new address) and their confirmations (per IP), admin invite links (per IP), Help page messages (per IP and address) and learners' support requests (per account) |
| `APP_DEFAULT_TIME_ZONE` | `Asia/Singapore` | zone of the platform day (streaks, daily goals, plan weeks, the scheduled jobs below) |
| `APP_SCHEDULING_ENABLED` | `true` | background jobs: the admins' daily summary e-mail (from 8:00 platform time, once a day, when AI reports or support requests are waiting), the learners' daily-goal reminder (from 19:00, once a day) and the weekly mistakes reminder (Mondays from 10:00, at least 5 waiting); the tests switch them off |
| `DEFAULT_ADMIN_EMAIL`, `DEFAULT_ADMIN_PASSWORD` | empty | first admin account (see above) |
| `DEMO_STUDENT_PASSWORD` | `DemoStudent123!` | password of the demo learners (`demo` profile) |
| `UPLOAD_DIR`, `RECORDED_VIDEO_UPLOAD_DIR` | `./uploads`, `./uploads/recorded-videos` | file storage |
| `MAX_FILE_SIZE`, `MAX_SUPPORT_SCREENSHOT_FILE_SIZE`, `MAX_AI_AUDIO_FILE_SIZE`, `MAX_AI_IMAGE_FILE_SIZE`, `MAX_VIDEO_FILE_SIZE`, `MAX_REQUEST_SIZE` | `10MB`, `5MB`, `10MB`, `10MB`, `200MB`, `201MB` | Generic, screenshot, audio, image, video and whole-request limits. Set the whole-request limit above the largest file limit to allow for form fields and multipart headers. |
| `AI_ENABLE_REAL_PROVIDER` | `false` | enable backend AI text, image and pronunciation requests. The frontend uses `VITE_TAVUS_DEPLOYMENT_ID` for live video calls. |
| `AI_PROVIDER`, `AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL_NAME` | `openai`, `https://api.openai.com/v1`, empty, `gpt-5.6-luna` | Text and image provider. `openai` uses Responses for text and handwriting photos. `deepseek` and `openai-compatible` use text-only Chat Completions. The base URL must be `https` (plain `http` only to localhost) without credentials, query or fragment. Locally, `app.ai.api-key-file` can also point to a file containing the API key (ignored when `AI_API_KEY` is set) |
| `TENCENT_SOE_SECRET_ID`, `TENCENT_SOE_SECRET_KEY`, `TENCENT_SOE_APP_ID` | empty | Tencent Cloud New Age pronunciation assessment. Requires a CAM SecretId, SecretKey and numeric Tencent Cloud account AppID |
| `AI_FFMPEG_PATH` | `ffmpeg` | FFmpeg executable for decoding pronunciation uploads |
| `AI_TIMEOUT_SECONDS` | `30` (allowed 1–40) | provider response deadline in seconds. Pronunciation clips streamed at playback speed get their duration plus five seconds in addition to this deadline. Audio decoding has a separate 15-second timeout |
| `AI_MAX_OUTPUT_TOKENS`, `AI_REQUESTS_PER_MINUTE`, `AI_REQUESTS_PER_DAY` | `1200` (allowed 1–4000), `10`, `100` (at least 1) | AI output size and limits per user; values out of range stop start-up |
| `SMTP_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | `false`, empty, `587`, empty, empty, empty | outgoing e-mail (STARTTLS required) |
| `FRONTEND_BASE_URL` / `PASSWORD_RESET_BASE_URL` | `http://localhost:5174` / `${FRONTEND_BASE_URL}/reset-password` | links in e-mails (reset, e-mail confirmation, invites, notification e-mails) |
| `VITE_API_BASE_URL` (frontend build) | `/api/v1` | API base URL of the built frontend |
| `VITE_API_PROXY_TARGET` (frontend dev) | `http://127.0.0.1:8080` | where the Vite dev server proxies `/api/v1` |
| `VITE_TAVUS_DEPLOYMENT_ID` (frontend build) | empty | public Tavus Embed deployment for live calls in Talk with Xiaomei |

### Production checklist

With the `prod` (or `production`) profile the backend refuses to start unless:

- `DB_SSL_MODE` is `REQUIRED`, `VERIFY_CA` or `VERIFY_IDENTITY` (`allowPublicKeyRetrieval` and `spring.flyway.baseline-on-migrate` are locked to `false`; any HikariCP override via `spring.datasource.hikari.jdbc-url`, `spring.datasource.hikari.data-source-properties.sslMode`, or `spring.datasource.hikari.data-source-class-name` must enforce one of these encrypted modes too);
- `JWT_SECRET` is a deployment-specific key. The production guard rejects documented placeholders and both bundled development keys. Set `JWT_REFRESH_COOKIE_SECURE=true`.
- `APP_CORS_ALLOWED_ORIGINS` lists explicit public `https` origins;
- `AI_ENABLE_REAL_PROVIDER=true` with a supported `AI_PROVIDER`, a real `AI_API_KEY`, `AI_MODEL_NAME` and a public
  `https` `AI_API_BASE_URL` without query or fragment;
- `SMTP_ENABLED=true` with `SMTP_HOST`, a valid sender address (`SMTP_FROM` or an e-mail address in `SMTP_USERNAME`),
  and a public `https` `PASSWORD_RESET_BASE_URL`. SMTP authentication is enabled by default and requires `SMTP_USERNAME`
  and `SMTP_PASSWORD`. The configured protocol (`smtp` by default, or `smtps`) must use required STARTTLS or implicit
  TLS with server-identity verification. The guard rejects `mail.smtp.ssl.trust`, `mail.smtps.ssl.trust` and custom
  socket factories for the active protocol.

Swagger UI (`/swagger-ui.html`) and the OpenAPI JSON (`/v3/api-docs`) are disabled in production.

For production CORS origins, the AI provider and password-reset links, startup checks the HTTPS scheme, port and
destination. Localhost names, private IP literals, link-local addresses and multicast addresses are rejected.
Configure a publicly reachable DNS name for each service. Startup does not resolve DNS names.

Build the frontend with `npm run build` and serve `frontend/dist` so that `/api/v1` reaches the backend (or build with
`VITE_API_BASE_URL` pointing at it and add that origin to `APP_CORS_ALLOWED_ORIGINS`).

## Tests and checks

Backend (needs the MySQL server; each Spring test context empties and rebuilds the tables of its own database
`TEST_DB_NAME`, default `ai_chinese_learning_test`, created if missing; `SchemaResetTests` also creates and drops
`<TEST_DB_NAME>_reset_check` (`schema_reset_check` when `TEST_DB_NAME` is unset), so the test user needs the right to
create and drop databases):

```bash
cd backend
DB_USERNAME=root DB_PASSWORD=... ./mvnw test
```

Concurrent test runs against one MySQL server must use different `TEST_DB_NAME` values.
The startup, scheduled job, hint, support and quiz concurrency tests observe row locks. Enable MySQL Performance
Schema and give the test user read access to `performance_schema.data_locks`.

`MySqlMigrationSmokeTests` builds the schema with the Flyway migrations and validates every JPA entity against
it; `MySqlMigrationUpgradeTests` fills earlier schema versions with data and checks that the later migrations keep and
convert it (V1→V2, V2→V3 with the teacher-era data, V3→V4, V6→V7 for weighted quiz scores, V7→V8 for the
removed duplicate counter indexes, V8→V9 for hints delivered before their practice run was recorded, V9→V10 for schema
integrity constraints and secondary indexes, and V10→V11 for `package_items` referential integrity, question-type
normalisation and constraint checks, and redundant-index cleanup).
Both are skipped unless enabled. The smoke test needs an existing, empty database; the upgrade test creates and drops
`<TEST_DB_NAME>_upgrade_check` (`migration_upgrade_check` when `TEST_DB_NAME` is unset), so the user needs the right to
create and drop databases:

```bash
RUN_MYSQL_MIGRATION_TEST=true DB_USERNAME=root DB_PASSWORD=... DB_NAME=an_empty_database JWT_SECRET=any-32-character-or-longer-secret ./mvnw test -Dtest='MySqlMigration*Tests'
```

Set `AI_FFMPEG_PATH` to an installed FFmpeg executable to include the audio decoder tests, which use synthetic audio.

Frontend:

```bash
cd frontend
npm run lint
npm run format:check
npm run typecheck
npm run test:unit
npm run build
```

Playwright builds the executable jar in `backend/target-e2e` so browser tests can run alongside Maven tests.
It starts the `demo` backend on port 8185 with its own MySQL database `ai_chinese_learning_e2e` and Vite on port 5185.
Set `DB_USERNAME` and `DB_PASSWORD` for MySQL before running the suite.

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

## Notes

- E-mail texts: the fixed words are in `backend/src/main/resources/i18n/emails*.properties` (English, Malay,
  Chinese). Notifications appear only in the app. Delivery starts after the business transaction commits. A delivery
  failure is logged and the submitted support request, AI report or published content stays saved. The notifications
  page translates the stored English text using `frontend/src/utils/serverMessageRows/notifications.ts`.
  The adjacent `notifications.cases.json` supplies translation fixtures for the frontend tests. These tests also check
  the catalogue against the backend's current wording. When you reword a notification, move its old English row to
  `RETIRED_NOTIFICATION_MESSAGES` so previously stored notifications still have a translation.
- Roles: learners (`STUDENT` internally) and admins. Admins make and publish the classes (Admin › Content › Classes);
  a lesson can be published once it has a video, key phrases, its three activities (Flashcards, Tracing, Matching) and
  a quiz of exactly five questions with every AI-drafted question checked; a class once it has two units with four to
  six published lessons. Migration `V3` removed the earlier teacher role: accounts left without a role were
  deactivated (their rows are kept). An account with both roles opens the admin dashboard by default. A direct
  link can open either workspace, with that workspace's navigation. Admin accounts cannot delete themselves.
- Signing out ends the session in that browser. Changing the password ends all other sessions while issuing a fresh
  session for this browser, and invalidates outstanding password-reset and e-mail-change links; resetting a forgotten
  password ends all sessions and invalidates outstanding e-mail-change links. A learner can request a fresh reset link
  afterwards. Deleting the account also ends all sessions.
- Account settings save a changed display name and daily goal independently. The language switch saves the
  account's preferred language. `PUT /api/v1/users/me/profile` accepts a display name, a preferred language, or both.
- The AI safety switches and public AI notice are read from the shared database. Once an admin saves a change,
  subsequent requests on every backend instance can use it.
- A learning plan built in the setup is saved as a draft and becomes the learner's plan on "Start my path"; "Set up
  later" discards it.
- Database changes go into a new Flyway migration in `backend/src/main/resources/db/migration`
  (`V<next number>__<description>.sql`); never edit an applied migration. The test and demo profiles build their tables
  from the entities instead, so run `MySqlMigrationSmokeTests` after changing an entity.
