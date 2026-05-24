package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * One mistake-review row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/wrong-answers} — paginated
 *       cross-quiz feed of every wrong answer the student has ever
 *       submitted, newest first. Drives the "Mistakes to review"
 *       widget on the student dashboard.</li>
 *   <li>{@code GET /api/v1/students/quiz-attempts/{attemptId}/wrong-answers}
 *       — non-paginated subset filtered to a single attempt.
 *       Drives the per-attempt review screen.</li>
 * </ul>
 *
 * <p>Carries everything the front-end needs to render a flashcard-
 * style review card: the question text, what the student answered,
 * what the right answer was, and the teacher's explanation. The
 * fields mirror the post-submission view
 * {@link QuizAttemptResultQuestionResponse}, with the parent
 * {@link #quizTitle} denormalised in so the cross-quiz feed can
 * label each card with its source.</p>
 *
 * <p>{@link #questionType} is the same enum string as on
 * {@link CreateQuizQuestionRequest} — see
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}.</p>
 */
public record WrongAnswerResponse(
        Long attemptId,
        Long questionId,
        String quizTitle,
        String questionText,
        String questionType,
        String studentAnswer,
        String correctAnswer,
        String explanation,
        LocalDateTime submittedAt) {
}
