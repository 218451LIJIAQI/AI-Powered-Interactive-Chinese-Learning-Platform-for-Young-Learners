# 🐼 MandarinSpark

Walk into Xiaomei’s café and she’ll ask, **“你想喝点儿什么？”** What would you like to drink? A beginner might know the word **茶** (*chá*, tea) and still need a moment to say it out loud. That little gap between recognizing a word and using it is where MandarinSpark lives. 🍵

MandarinSpark is a desktop web app for young learners who are starting Mandarin. It brings short practice sessions, themed classes, a personal learning plan and optional AI coaching into one place. The interface speaks English, Bahasa Melayu and Simplified Chinese, so learners can change the language of the instructions while keeping Mandarin as the language they practise. [Meet Xiaomei and browse her scenarios](frontend/src/components/ai/aiHelper.ts)

> 🍵 **Your turn at the counter.** 我想喝茶。 · *Wǒ xiǎng hē chá.* · “I’d like some tea.” Read it once with the pinyin, then cover the middle and try it again.

## 🌱 What shall we learn today?

| If you have… | Open… | What happens there |
| --- | --- | --- |
| A goal and no idea where to start 🧭 | **Level Check & My Plan** | Take the level check, pick a goal and a study pace, preview the suggested weeks, then choose **Start my path**. |
| A few minutes between classes 🎧 | **Practice** | Pick a listening set, trace a character or try pinyin typing. Your answers and confidence give you something concrete to revisit. |
| One stubborn word 🃏 | **Mistakes review** | Revisit wrong answers and flashcards marked **Still learning**. Correct answers clear when you finish the review. |
| Some uninterrupted study time 📚 | **Classes** | Work through a themed unit with video, key phrases, flashcards, tracing, matching and a lesson quiz. |
| A sentence you want to *use* 💬 | **AI Helper** | Try a café conversation, ask directions, plan the weekend or order food at a *pasar malam* with Xiaomei. |

The learning path keeps XP, streaks, badges and checkpoints visible. Pick a daily target of 10, 20 or 30 XP. Reaching it for the first time that day earns a 5 XP bonus, and every 100 XP advances the XP level. The level check separately assesses Mandarin proficiency, so a learner can celebrate a busy week and still get practice pitched at the right difficulty. [Reward rules](backend/src/main/java/com/example/aichinese/service/RewardRules.java) · [Level check](backend/src/main/java/com/example/aichinese/service/learn/LevelCheckService.java)

## 🃏 The flashcard that asks for a rematch

Tap **Still learning** on a flashcard and it gets a second life as a review question. A review serves up to 20 waiting items. Finish with one or more correct answers and those items clear. The daily review award adds **10 XP** once that day. A missed answer stays available for another go. That makes the review queue a pretty good record of what *you* find tricky. [Follow a mistake from first miss to review](backend/src/main/java/com/example/aichinese/service/learn/MistakeService.java)

There are six practice formats. **Flashcards** train recall, **listening** starts with the sound, and **tracing** puts the shape of the character in your hand. **Drag-and-drop** and **matching** make you arrange or pair the pieces. In **pinyin typing**, you read a meaning, type the pinyin and choose the characters it can spell. Listening, drag-and-drop and typing also let you say whether you were *guessing* or *sure*. [Practice formats](backend/src/main/java/com/example/aichinese/entity/enums/PracticeType.java) · [Practice screen](frontend/src/pages/student/StudentPracticePage.vue)

## 🗺️ Where a plan gets its lessons

1. **The level check sets the starting point.** The learner supplies the goal and available study time. The backend grades the check and selects steps from the real catalogue.
2. **The plan composer lays out the weeks.** The pace influences the schedule. When available, SparkBot can interpret the goal, suggest an order for candidate classes and explain why a step belongs. The server checks the returned references. The rules can build the plan on their own.
3. **The learner has the last word.** A preview becomes active on **Start my path**. A changed goal or a new placement level can produce another version. Completed work continues to count, and the learner’s daily XP goal remains an account setting.

The plan service, scheduling rules and optional AI adviser each own a different part of that decision. [Plan lifecycle](backend/src/main/java/com/example/aichinese/service/plan/LearningPlanService.java) · [Week-by-week composer](backend/src/main/java/com/example/aichinese/service/plan/PlanComposer.java) · [SparkBot’s contribution](backend/src/main/java/com/example/aichinese/service/plan/PlanAdvisor.java)

## 🎙️ Inside the AI practice room

| Activity | What the learner gets | What powers it |
| --- | --- | --- |
| 💬 **Talk with Xiaomei** | Scenario openers with Chinese and pinyin, a conversation transcript and a session summary. A configured deployment can bring Xiaomei on screen for a live call. | The conversation service and optional Tavus Embed integration. |
| ✨ **Ask SparkBot** | A place to ask a language question and read the reply as it arrives. | The backend AI gateway handles provider calls, safety checks and per-user usage limits. |
| 📖 **Stories & quick quizzes** | A short reading task or quiz built around a topic. The story view lets a learner reveal pinyin as needed. | Structured generation results are rendered by dedicated Vue components. |
| 🎤 **Pronunciation** | Record a target phrase and inspect feedback about the attempt. | FFmpeg decodes the upload to 16 kHz mono PCM for Tencent Cloud Smart Oral Evaluation. |
| ✍️ **Handwriting** | Submit a photo of a character and get feedback on visible strokes and proportions. An unclear photo can be retaken. | An image-capable OpenAI model evaluates the photo. Recent practice checks retain the result without retaining the submitted recording or image. |

Text and handwriting use the configured backend provider. Voice assessment needs Tencent credentials and FFmpeg. Live calls need a Tavus deployment allowed to run on the frontend’s origin. Each activity has its own setup, which makes it practical to introduce them one at a time. [AI Helper](frontend/src/pages/student/StudentAiHelperPage.vue) · [AI gateway](backend/src/main/java/com/example/aichinese/service/ai/AiGateway.java) · [Practice checks](backend/src/main/java/com/example/aichinese/service/AiPracticeCheckService.java)

## 🧑‍🏫 Who fills the classroom?

The MandarinSpark admin team builds the learning path, authors classes and manages practice sets. A publishable lesson needs its video, key phrases, three activities and a five-question quiz. AI-drafted quiz questions go through human review. Admins can also reply to support requests, review flagged AI responses and follow activity through analytics and the audit log. The language selector carries through to account preferences and localized email. [Admin learning path](frontend/src/pages/admin/AdminLearningPathPage.vue) · [AI reports](frontend/src/pages/admin/AdminAiReportsPage.vue) · [Mail translations](backend/src/main/resources/i18n/)

## 🧰 Under the desk

| Layer | Implementation |
| --- | --- |
| Browser | Vue 3, TypeScript, Vite, Pinia, Vue Router and Vue I18n. Route guards direct learners and admins to their workspaces. |
| API | Java 21 and Spring Boot 3.5. Spring Security handles JWT access tokens and rotating refresh tokens delivered through an HttpOnly cookie. Vite proxies development traffic under `/api/v1`. |
| Persistence | MySQL 8 with Spring Data JPA. Flyway manages the persistent schema and Hibernate validates it at startup. Plans, lesson completions, mistakes and rewards have their own server-side records. |
| Verification | Maven checks backend behavior, Vitest covers frontend logic and components, and Playwright exercises browser journeys against the demo backend. |

Start with the [frontend manifest](frontend/package.json), [backend build](backend/pom.xml) or [schema migrations](backend/src/main/resources/db/migration/).

## 🚀 Open your own classroom

Use **JDK 21**, **Node.js 22** and **MySQL 8**. For the persistent local profile, create an `utf8mb4` database and grant an application user access. The defaults are database `ai_chinese_learning`, user `aichinese_app` and port **3307**. Set `DB_PASSWORD` and adjust `DB_HOST`, `DB_PORT`, `DB_NAME` or `DB_USERNAME` to match your setup. To create the initial administrator, set `DEFAULT_ADMIN_EMAIL` and `DEFAULT_ADMIN_PASSWORD` together.

| Terminal | Working directory | Command |
| --- | --- | --- |
| Backend | `backend/` | `./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run` on Windows |
| Frontend | `frontend/` | `npm ci`, then `npm run dev` |

Open `http://localhost:5174`. The local API runs on port **8080**. Start with the local database profile for work you want to keep. The **demo** profile rebuilds and seeds its dedicated database on every startup. [Base configuration](backend/src/main/resources/application.yml) · [Local profile](backend/src/main/resources/application-local.yml) · [Demo profile](backend/src/main/resources/application-demo.yml)

<details>
<summary>🔧 Optional integrations and test commands</summary>

- **Text and images.** Enable `AI_ENABLE_REAL_PROVIDER` and configure `AI_PROVIDER`, `AI_MODEL_NAME` and the backend API key. The local configuration example also supports a private key file. OpenAI Responses supports the handwriting flow. The OpenAI-compatible text route serves text tasks. [Local configuration example](backend/config/application-local.yml.example)
- **Voice and video.** The pronunciation client reads `TENCENT_SOE_SECRET_ID`, `TENCENT_SOE_SECRET_KEY` and `TENCENT_SOE_APP_ID`. Set `AI_FFMPEG_PATH` if FFmpeg is outside the executable search path. The frontend’s `VITE_TAVUS_DEPLOYMENT_ID` is a public embed identifier. [Pronunciation client](backend/src/main/java/com/example/aichinese/service/ai/TencentPronunciationClient.java) · [Frontend environment example](frontend/.env.local.example)
- **Deployment profiles.** The `prod` and `production` profiles check the database TLS mode, signing key, secure refresh cookie, HTTPS origins, AI provider and email settings at startup. Outgoing email uses the SMTP settings and links back to the configured frontend. The base configuration lists the environment variables and defaults in one place. [Runtime settings](backend/src/main/resources/application.yml) · [Production guards](backend/src/main/java/com/example/aichinese/config/)
- **Backend checks.** Run `./mvnw test` from `backend/` with MySQL credentials and a dedicated `TEST_DB_NAME`. The suite rebuilds its test schema. Migration smoke and upgrade checks have separate opt-in switches. [Migration smoke test](backend/src/test/java/com/example/aichinese/MySqlMigrationSmokeTests.java)
- **Frontend checks.** From `frontend/` run `npm run typecheck`, `npm run test:unit` and `npm run build`. For browser tests, install Chromium with `npx playwright install chromium`, configure MySQL and run `npm run test:e2e`. [Browser test configuration](frontend/playwright.config.ts)

</details>
