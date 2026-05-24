package com.example.aichinese.dto.learning;

import java.util.List;

/**
 * Pre-submission view of one quiz question, surfaced inside
 * {@link StudentQuizResponse#questions()}.
 *
 * <p>Deliberately omits both {@code correctAnswerText} and the
 * teacher's {@code explanation} — those fields are revealed only
 * <em>after</em> the student submits, on
 * {@link QuizAttemptResultQuestionResponse}. {@link #options} is a
 * list of {@link QuizOptionResponse} rows that themselves omit the
 * {@code correctOption} flag for the same anti-cheat reason. For
 * non-option-based question types ({@code FILL_BLANK},
 * {@code REARRANGE_SENTENCE}) the list will be empty and the
 * front-end renders the appropriate input control based on
 * {@link #questionType}.</p>
 *
 * <p>{@link #displayOrder} is the canonical authoring order; the
 * front-end may shuffle for replay value but the server never
 * relies on the order for grading.</p>
 */
public record StudentQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        Integer points,
        Integer displayOrder,
        List<QuizOptionResponse> options) {
}
