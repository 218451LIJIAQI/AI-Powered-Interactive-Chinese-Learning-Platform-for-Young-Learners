package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One multiple-choice option attached to a quiz question.
 *
 * <p>Replayed as part of {@link CreateQuizQuestionRequest#options()}
 * or {@link UpdateQuizQuestionRequest#options()} — the parent
 * question owns the lifecycle, and the entire option list is
 * replaced atomically on each save.</p>
 *
 * <p>{@link #correctOption} is primitive {@code boolean} so the JSON
 * wire format always emits {@code true}/{@code false} without null
 * ambiguity. Multiple options may be flagged correct for question
 * types that require it (e.g. {@code MATCH_WORD_MEANING}); the
 * scorer ({@code QuizScoringService}) resolves correctness against
 * the parent question's {@link com.example.aichinese.entity.enums.QuizQuestionType}.</p>
 *
 * <p>Note that the student-facing counterpart
 * {@link QuizOptionResponse} <em>omits</em> {@link #correctOption}
 * to prevent the answer from leaking before the student submits.
 * Only the teacher-facing {@link TeacherQuizOptionResponse} surfaces
 * the correct flag.</p>
 */
public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        @Size(max = 1000, message = "Option text must be 1000 characters or fewer.")
        String optionText,
        boolean correctOption,
        @PositiveOrZero(message = "Option display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
