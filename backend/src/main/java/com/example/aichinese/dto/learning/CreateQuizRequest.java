package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/lessons/{lessonId}/quizzes} —
 * the teacher creates an empty quiz shell. Questions are added in
 * follow-up calls to {@code POST /quizzes/{quizId}/questions} (one
 * {@link CreateQuizQuestionRequest} per question) so the authoring
 * UI can save the header first and stream questions in afterwards
 * without losing partial work.
 *
 * <p>The newly created quiz is owned by the lesson's teacher and
 * starts in {@link com.example.aichinese.entity.enums.ContentStatus#DRAFT}
 * until the teacher publishes it via the lesson workflow. It is not
 * visible to students until then.</p>
 *
 * <h2>{@link #timeLimitMinutes}</h2>
 * <p>{@code 0} or {@code null} both mean "untimed" — the
 * student-side {@code StudentQuizResponse} surfaces the same value
 * verbatim and the front-end interprets it. {@link PositiveOrZero}
 * lets {@code 0} act as the explicit "no time limit" sentinel without
 * collapsing it to {@code null} (which clients sometimes send as
 * the missing-field value during partial saves).</p>
 */
public record CreateQuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes) {
}
