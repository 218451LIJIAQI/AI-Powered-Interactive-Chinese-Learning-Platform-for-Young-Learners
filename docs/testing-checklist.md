# Testing Checklist

Use this checklist for final manual verification and FYP demonstration readiness. Mark each item as `PASS`, `FAIL`, or `N/A`.

## 1. Environment Setup
- [ ] Start MySQL with `docker compose up -d`
- [ ] Run `docker compose config`
- [ ] Start backend with `.\mvnw.cmd spring-boot:run`
- [ ] Start frontend with `npm run dev`
- [ ] Confirm `GET /api/v1/health` returns `UP`

## 2. Authentication
- [ ] Student registration
- [ ] Teacher registration
- [ ] Admin login
- [ ] Login failure with invalid credentials
- [ ] Logout
- [ ] Current user API
- [ ] Route protection redirect for unauthenticated users

## 3. Role-Based Access
- [ ] Student cannot access teacher pages
- [ ] Student cannot access admin pages
- [ ] Teacher cannot access admin pages
- [ ] Admin can access admin pages
- [ ] Guest cannot access protected pages
- [ ] Unauthorized backend access returns `401`
- [ ] Wrong-role backend access returns `403`

## 4. Teacher Verification
- [ ] Teacher starts in `PENDING`
- [ ] Verification document upload works
- [ ] Admin approval works
- [ ] Admin rejection works
- [ ] Teacher dashboard shows verification state clearly

## 5. Student Learning Flow
- [ ] View learning modules or lessons
- [ ] Start a lesson
- [ ] Complete an activity
- [ ] Submit a quiz
- [ ] View wrong answers
- [ ] View progress
- [ ] Earn points or a reward

## 6. Interactive Activities
- [ ] Flashcards
- [ ] Quiz
- [ ] Matching game
- [ ] Listening activity
- [ ] Character tracing

## 7. AI Features
- [ ] AI Tutor
- [ ] AI Vocabulary Helper
- [ ] AI Sentence Practice
- [ ] AI Pronunciation Feedback prototype
- [ ] AI Writing Feedback prototype
- [ ] AI Quiz Generator
- [ ] AI Story Generator
- [ ] AI Learning Recommendation
- [ ] AI Progress Insight
- [ ] AI Teacher Assistant
- [ ] Friendly AI unavailable fallback when no real key is enabled

## 8. Payment and Subscription
- [ ] Add to cart
- [ ] Buy now
- [ ] Stripe sandbox checkout or unavailable mode
- [ ] Payment success or mock success behaviour
- [ ] Payment failure or cancel handling
- [ ] Subscription checkout
- [ ] Paid content unlock behaviour where applicable
- [ ] Payment history

## 9. Refund and Payout
- [ ] Student refund request
- [ ] Admin refund approval or rejection
- [ ] Teacher earnings view
- [ ] Teacher payout request
- [ ] Admin payout approval or rejection

## 10. Live, Recorded, and Hybrid Learning
- [ ] Teacher creates a live class
- [ ] Student enrols in a live class
- [ ] Student joins only when allowed
- [ ] Meeting link remains protected
- [ ] Teacher adds a recorded video or external video link
- [ ] Student watches a recorded lesson
- [ ] Video progress updates
- [ ] Student schedule
- [ ] Teacher schedule
- [ ] Admin schedule

## 11. Reports and Reviews
- [ ] Student submits a report
- [ ] Admin handles a report
- [ ] Student submits a review
- [ ] Teacher views own reviews
- [ ] Admin moderates a review

## 12. Support Tickets
- [ ] Student submits a support ticket
- [ ] Teacher submits a support ticket
- [ ] Admin replies to a ticket
- [ ] Admin updates ticket status
- [ ] Closed ticket behaviour is enforced

## 13. Notifications
- [ ] Student notifications load
- [ ] Teacher notifications load
- [ ] Admin notifications load
- [ ] Unread count updates
- [ ] Mark as read works
- [ ] Mark all as read works

## 14. File Uploads
- [ ] Teacher verification document upload
- [ ] Image upload flow
- [ ] Audio upload flow
- [ ] Video upload flow
- [ ] Invalid file type handling
- [ ] Oversized file handling

## 15. Analytics
- [ ] Student dashboard analytics loads
- [ ] Teacher dashboard analytics loads
- [ ] Admin dashboard analytics loads
- [ ] Analytics charts handle empty data cleanly
- [ ] No role leakage across analytics endpoints

## 16. Responsive Design
- [ ] Desktop layout
- [ ] Tablet layout
- [ ] Mobile layout

## 17. i18n
- [ ] English
- [ ] Bahasa Melayu
- [ ] Chinese
- [ ] Main pages do not show raw translation keys

## 18. Theme
- [ ] Light mode
- [ ] Dark mode
- [ ] Text contrast remains readable

## 19. Legal and Safety Pages
- [ ] Terms of Service
- [ ] Privacy Policy
- [ ] Child Safety
- [ ] AI Use Disclaimer

## 20. Final Demo Flow
- [ ] Suggested demo order is rehearsed
- [ ] Talking points are prepared
- [ ] Backup plan is ready if AI keys are missing
- [ ] Backup plan is ready if Stripe is unavailable
- [ ] Backup plan is ready if SMTP is disabled

## Automated Verification Commands
Frontend:

```powershell
cd frontend
npm run build
npm run test
```

Backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

Infrastructure:

```powershell
docker compose config
```

## Security and Static Checks
- [ ] No `javax.persistence` imports
- [ ] No password hash exposure
- [ ] No hard-coded real AI key
- [ ] No hard-coded real Stripe secret
- [ ] No hard-coded real SMTP credential
- [ ] No hard-coded JWT secret
- [ ] No backend secrets exposed in frontend
- [ ] No staged runtime, upload, log, build, target, or temp files
- [ ] `frontend/src/i18n/messages/zh.ts` remains clean UTF-8
