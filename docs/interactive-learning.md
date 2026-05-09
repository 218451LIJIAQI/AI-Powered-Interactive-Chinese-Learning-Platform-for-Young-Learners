# Phase 7 Interactive Learning

## Purpose
Phase 7 adds the interactive learning foundation:
- student lesson player
- flashcard practice
- quiz submission and scoring
- matching, listening, and tracing completion
- wrong-answer review
- progress tracking
- points, badges, and streak summaries
- teacher activity management

## What Phase 7 Includes
Student flow:
- open a published lesson
- view lesson activities
- practise flashcards
- take quizzes
- submit matching games
- submit listening answers
- complete tracing activities
- review wrong answers
- view progress and rewards

Teacher flow:
- create and edit flashcards
- create quizzes and questions
- create matching games
- create listening exercises
- create tracing exercises
- view quiz attempts for owned lessons

## What Phase 7 Does Not Implement
- AI tutor or AI explanations
- payment, subscription, checkout, cart, or unlock logic
- leaderboard
- professional handwriting recognition
- professional pronunciation scoring
- live class workflows
- recorded video workflow
- Phase 8 or later features

## Access Rules
- `STUDENT` can access only `PUBLISHED` lesson activities.
- paid content stays locked in public and student browsing.
- `TEACHER` must still be `APPROVED` to create or update activity content.
- teachers can only manage activities for their own lessons.
- `ADMIN` routes remain unchanged and protected.

## Backend APIs
Student APIs:
- `GET /api/v1/students/lessons/{lessonId}`
- `GET /api/v1/students/lessons/{lessonId}/activities`
- `GET /api/v1/students/lessons/{lessonId}/flashcards`
- `POST /api/v1/students/flashcards/{flashcardId}/practice`
- `GET /api/v1/students/quizzes/{quizId}`
- `POST /api/v1/students/quizzes/{quizId}/attempts`
- `GET /api/v1/students/quiz-attempts/{attemptId}`
- `GET /api/v1/students/wrong-answers`
- `GET /api/v1/students/quiz-attempts/{attemptId}/wrong-answers`
- `GET /api/v1/students/matching-games/{gameId}`
- `POST /api/v1/students/matching-games/{gameId}/submit`
- `GET /api/v1/students/listening-exercises/{exerciseId}`
- `POST /api/v1/students/listening-exercises/{exerciseId}/submit`
- `GET /api/v1/students/tracing-exercises/{exerciseId}`
- `POST /api/v1/students/tracing-exercises/{exerciseId}/complete`
- `GET /api/v1/students/progress`
- `GET /api/v1/students/rewards`
- `GET /api/v1/students/rewards/badges`
- `GET /api/v1/students/rewards/points-history`
- `GET /api/v1/students/rewards/streak`

Teacher APIs:
- `GET/POST /api/v1/teachers/lessons/{lessonId}/flashcards`
- `PUT/DELETE /api/v1/teachers/flashcards/{flashcardId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/quizzes`
- `PUT/DELETE /api/v1/teachers/quizzes/{quizId}`
- `POST /api/v1/teachers/quizzes/{quizId}/questions`
- `PUT/DELETE /api/v1/teachers/quiz-questions/{questionId}`
- `GET /api/v1/teachers/quizzes/{quizId}/attempts`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/matching-games`
- `PUT/DELETE /api/v1/teachers/matching-games/{gameId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/listening-exercises`
- `PUT/DELETE /api/v1/teachers/listening-exercises/{exerciseId}`
- `GET/POST /api/v1/teachers/lessons/{lessonId}/tracing-exercises`
- `PUT/DELETE /api/v1/teachers/tracing-exercises/{exerciseId}`

## Quiz Rules
- quiz answers are not exposed before submission
- score is calculated on the backend
- wrong answers are stored with the attempt
- students can only view their own attempts and wrong answers
- teachers can only view attempts for quizzes in their own lessons

## Reward Rules
Current backend reward values:
- flashcard set completion: `5`
- matching game completion: `10`
- listening exercise completion: `10`
- tracing completion: `10`
- quiz completion: `20`
- quiz score at least `80%`: `10` bonus
- lesson completion: `25`

Reward safeguards:
- backend-owned point calculation
- duplicate one-time rewards avoided where practical
- daily streak updates on new learning days

## Frontend Pages
Student:
- `/student/lessons`
- `/student/lessons/:lessonId`
- `/student/lessons/:lessonId/flashcards`
- `/student/quizzes/:quizId`
- `/student/quiz-attempts/:attemptId`
- `/student/matching-games/:gameId`
- `/student/listening-exercises/:exerciseId`
- `/student/tracing-exercises/:exerciseId`
- `/student/progress`
- `/student/rewards`
- `/student/wrong-answers`

Teacher:
- `/teacher/flashcards`
- `/teacher/quizzes`
- `/teacher/matching-games`
- `/teacher/listening-exercises`
- `/teacher/tracing-exercises`
- `/teacher/quiz-results`

## Manual Test Guide
Teacher:
1. Log in as an approved teacher.
2. Create a lesson draft if needed.
3. Add flashcards.
4. Add a quiz and at least one question.
5. Add a matching game.
6. Add a listening exercise.
7. Add a tracing exercise.
8. Submit the lesson through the Phase 6 content workflow.

Student:
1. Log in as a student.
2. Open `/student/lessons`.
3. Open a published lesson.
4. Practise flashcards.
5. Submit a quiz.
6. Review the quiz result.
7. Open wrong-answer review.
8. Submit matching and listening activities.
9. Complete tracing.
10. Open progress and rewards pages.

## Verification Notes
- no AI implementation was added
- no payment workflow was added
- no leaderboard was added
- no `javax.persistence` imports should exist
- no password hash should be exposed
- no hard-coded JWT secret should be added
