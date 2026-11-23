# MandarinSpark

The campus canteen queue reaches you. You remember **一杯茶** (*yì bēi chá*, a cup of tea). Now, how do you ask the price? **多少钱？** (*duōshao qián?*) is the next phrase worth remembering. Both appear in *Ordering Food at the Canteen*, one of MandarinSpark’s demo lessons. [Read the campus lesson](backend/src/main/java/com/example/aichinese/config/seed/DemoClassContent.java)

MandarinSpark helps Malaysian beginners aged 18–25 learn Mandarin through short lessons, teacher-led classes and conversation practice. Learners can use the interface in English, Bahasa Melayu or Simplified Chinese.

## Take a phrase for a spin

- **Find a starting point.** Choose a goal and a pace, then try an adaptive level check. The plan builder picks steps from the actual learning catalogue. Change your goal or retake the check to see the plan change with you. [Learning plan rules](backend/src/main/java/com/example/aichinese/service/plan/LearningPlanService.java)
- **Practise the part that slips away.** Listening, tracing, matching and story activities exercise different skills. Missed items go into a review queue. Answer one correctly in a review and it comes off the list. [Mistake review](backend/src/main/java/com/example/aichinese/service/learn/MistakeService.java)
- **Give the words a speaking part.** With AI enabled, Xiaomei takes the other side of an everyday conversation, offers reply ideas and wraps up with feedback. SparkBot handles quick explanations. A learner can also ask for a short story with pinyin and comprehension questions. [Xiaomei’s conversation service](backend/src/main/java/com/example/aichinese/service/XiaomeiService.java)

Teachers can turn a phrase into a lesson with video, tracing activities and a quiz, preview it, then submit it for review. Students can join a published class and book a live session. Teachers see quiz results and weak areas, while administrators review teacher applications, class content and reported AI replies.

## How the learning loop works

A **Vue 3 and TypeScript** client uses Pinia for shared state and Vue I18n for the three interface languages. A **Java 21 / Spring Boot** API owns the level check, catalogue, review queue and XP rules. AI can help word a learning plan and give feedback. Completed learning and practice activities earn XP. The OpenAI adapter sets `store=false` on its Responses API requests. [Provider implementation](backend/src/main/java/com/example/aichinese/service/ai/OpenAiResponsesAiProviderClient.java)

**MySQL 8.4 and Flyway** handle persistent schema changes. The seeded demo uses H2. The verification workflow covers JUnit, Vitest, Playwright and migrations against MySQL. [Verification workflow](.github/workflows/verify.yml)

## Try the campus lesson

On Windows with Java 21 and Node.js 22, double-click `Start FYP.cmd` from a local checkout. It starts both services and opens `http://127.0.0.1:5174`. Sign in as `student@fyp.local` using the generated student password in `.local/admin.env`. Open *Mandarin for Beginners* and look under *Campus Life* for the canteen lesson. Run `Stop FYP.cmd` when you finish. [Launcher implementation](scripts/local.ps1)

MandarinSpark was developed as a Universiti Putra Malaysia final-year project and is designated *Proprietary - UPM Final Year Project*.
