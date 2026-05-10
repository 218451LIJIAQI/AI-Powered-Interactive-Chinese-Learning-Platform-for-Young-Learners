# Demo Script

## Demo Goal
Show that the project is a polished, honest, end-to-end FYP prototype covering learning, AI assistance, payments, live learning, moderation, support, analytics, i18n, and safety pages.

## 1. Landing Page
What to click:
- Open `/`

What to say:
- Introduce the platform as an AI-assisted Chinese learning system for young learners with student, teacher, and admin roles.

Expected result:
- The landing page looks presentable, branded, and child-friendly.

Backup plan:
- If backend data is limited, explain that the landing page focuses on navigation and product framing.

## 2. Student Registration and Login
What to click:
- Open `/register`
- Create a student account or use an existing test account
- Log in through `/login`

What to say:
- Explain that students can register directly and are routed into a role-protected dashboard.

Expected result:
- Successful login and redirect to `/student/dashboard`

## 3. Student Dashboard and Analytics
What to click:
- Open the student dashboard
- Point to stat cards, charts, quick actions, and recent activity areas

What to say:
- Highlight that the dashboard now uses real summaries and safe empty states instead of fake demo numbers.

Expected result:
- Analytics cards and charts render without layout breakage

Backup plan:
- If the account has little data, explain the zero-state design and honest empty analytics handling.

## 4. AI Tutor
What to click:
- Open the AI Tutor page
- Submit a simple prompt

What to say:
- Explain that AI is integrated behind a backend provider abstraction and remains optional for demo purposes.

Expected result:
- Either a valid AI response or a friendly unavailable fallback

Backup plan:
- If no real AI key is configured, explicitly show the graceful fallback and explain that this is intentional.

## 5. AI Vocabulary Helper
What to click:
- Open the AI Vocabulary Helper page
- Enter a simple word or phrase

What to say:
- Explain that AI can support vocabulary, sentence practice, and other guided learning tools.

Expected result:
- Response or friendly fallback

## 6. Lesson, Quiz, and Activity Flow
What to click:
- Open a lesson
- Show flashcards, quiz, matching, listening, or tracing if demo content exists

What to say:
- Show that the platform supports interactive activities, progress, and reward logic rather than static content only.

Expected result:
- Student learning flow works without route or role issues

## 7. Live, Recorded, and Hybrid Learning
What to click:
- Open live classes, schedule, or a recorded lesson

What to say:
- Explain the protected live-class join flow, recorded learning support, and hybrid delivery model.

Expected result:
- Schedule and lesson access load normally

## 8. Payment and Subscription Overview
What to click:
- Open cart, payment history, subscription, or payment result pages

What to say:
- Explain that Stripe integration is prototype-friendly and sandbox-oriented.

Expected result:
- Payment pages load and clearly communicate test-mode or fallback behaviour

Backup plan:
- If no Stripe config is active, explain the documented unavailable or mock-mode behaviour.

## 9. Teacher Login and Dashboard
What to click:
- Log in as a teacher
- Open `/teacher/dashboard`

What to say:
- Show the professional teacher workspace and approval-aware content access behaviour.

Expected result:
- Teacher dashboard loads with analytics cards and charts

## 10. Teacher Analytics
What to click:
- Point to content status, quiz trend, earnings trend, and rating breakdown

What to say:
- Explain that teacher analytics are scoped to owned content and teacher-related aggregates only.

Expected result:
- Charts render cleanly without exposing unrelated student data

## 11. AI Teacher Assistant
What to click:
- Open the teacher AI assistant

What to say:
- Explain that teachers can generate drafts, but AI output must still be reviewed before publication.

Expected result:
- Draft workflow or friendly unavailable state

## 12. Teacher Live Class, Content, and Feedback
What to click:
- Open lesson management, live classes, recorded lessons, or feedback pages

What to say:
- Highlight the end-to-end teacher workflow beyond simple dashboard summaries.

Expected result:
- Teacher management pages remain usable and route-safe

## 13. Admin Dashboard and Analytics
What to click:
- Log in as admin
- Open `/admin/dashboard`

What to say:
- Explain that admins get a platform-level view for operations and demonstration oversight.

Expected result:
- Admin analytics cards and charts render successfully

## 14. Teacher Approval and Content Moderation
What to click:
- Open teacher approval and content approval/moderation pages

What to say:
- Show that trust and governance workflows are part of the system design.

Expected result:
- Admin management pages remain accessible and coherent

## 15. Reports, Reviews, Support, and Audit Logs
What to click:
- Open report management, review moderation, support tickets, and audit logs

What to say:
- Explain that communication and moderation workflows were completed before Phase 12 and are preserved in the final demo build.

Expected result:
- These pages still load and reflect role-protected workflows

## 16. i18n, Theme, and Legal Pages
What to click:
- Switch languages
- Toggle light and dark theme
- Open terms, privacy, child safety, and AI disclaimer pages

What to say:
- Show that the main UI is localised, theme-aware, and clearer for examiners and users.

Expected result:
- Language switching and theme toggle work without broken labels

## 17. Final Summary
What to say:
- Summarize the project as a full-stack educational prototype covering:
- authentication and role access
- learning activities and rewards
- AI support with graceful fallback
- payments and subscriptions in sandbox mode
- live and recorded learning
- moderation, support, audit, and notifications
- analytics dashboards
- i18n, legal pages, testing, and documentation

## Demo Backup Notes
- If AI keys are missing: show the unavailable fallback and explain backend provider abstraction
- If Stripe is unavailable: show test-mode documentation and non-crashing payment UX
- If SMTP is missing: show in-app notifications and explain optional email behaviour
- If analytics data is sparse: show honest zero-state charts and explain that no fake production records were inserted
