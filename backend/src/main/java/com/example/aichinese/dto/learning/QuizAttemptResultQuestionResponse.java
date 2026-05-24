package com.example.aichinese.dto.learning;

/**
 * Per-question breakdown emitted inside
 * {@link QuizAttemptResultResponse#answers()}.
 *
 * <p>This is the <em>post-submission</em> view, so unlike
 * {@link StudentQuizQuestionResponse} (the pre-submission view) it
 * intentionally surfaces both the {@link #studentAnswer} and the
 * {@link #correctAnswer} side-by-side along with the teacher's
 * {@link #explanation}. The student needs the answer revealed once
 * grading is complete in order to learn from the attempt — that is
 * the entire purpose of the review screen.</p>
 *
 * <p>{@link #questionType} is the same enum string as on
 * {@link CreateQuizQuestionRequest} (a
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * constant name). The front-end uses it to pick the correct
 * renderer for {@link #studentAnswer} / {@link #correctAnswer} —
 * for {@code REARRANGE_SENTENCE} this is a structured payload, for
 * option-based types it is the option label, for
 * {@code FILL_BLANK} it is plain text.</p>
 *
 * <p>{@link #correct} is the per-question outcome (primitive
 * {@code boolean}); {@link #points} is the points awarded by THIS
 * answer (zero when {@link #correct} {@code == false} or when the
 * question was unanswered — though unanswered questions do not
 * appear in this list at all).</p>
 */
public record QuizAttemptResultQuestionResponse(
        Long questionId,
        String questionType,
        String questionText,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Integer points,
        String explanation) {
}
