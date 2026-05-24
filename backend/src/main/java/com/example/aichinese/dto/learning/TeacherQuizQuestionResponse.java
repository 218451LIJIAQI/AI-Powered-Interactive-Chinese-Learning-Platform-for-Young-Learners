package com.example.aichinese.dto.learning;

import java.util.List;

/**
 * Teacher-facing view of one quiz question, surfaced inside
 * {@link TeacherQuizResponse#questions()}.
 *
 * <p>Mirrors {@link CreateQuizQuestionRequest} field-for-field plus
 * the database-assigned {@link #id}. Critically, this DTO surfaces
 * {@link #correctAnswerText} and {@link #explanation} — both fields
 * are stripped from the student-facing
 * {@link StudentQuizQuestionResponse} until the student submits an
 * attempt and receives the post-submission
 * {@link QuizAttemptResultQuestionResponse} review payload.</p>
 *
 * <p>{@link #options} carries {@link TeacherQuizOptionResponse}
 * rows which expose the per-option {@code correctOption} flag (the
 * student-facing {@link QuizOptionResponse} omits it). For
 * non-option-based question types
 * ({@link com.example.aichinese.entity.enums.QuizQuestionType#FILL_BLANK},
 * {@link com.example.aichinese.entity.enums.QuizQuestionType#REARRANGE_SENTENCE})
 * the list is empty.</p>
 */
public record TeacherQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        String correctAnswerText,
        Integer points,
        Integer displayOrder,
        String explanation,
        List<TeacherQuizOptionResponse> options) {
}
