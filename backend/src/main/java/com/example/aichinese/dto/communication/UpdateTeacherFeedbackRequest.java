package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/feedback/{id}} — a teacher
 * edits one of their previously-submitted feedback rows.
 *
 * <p>Strictly leaner than {@link CreateTeacherFeedbackRequest}:
 * the target ({@code studentId}, {@code lessonId},
 * {@code quizAttemptId}) is fixed at create time and cannot be
 * changed. Only the editorial fields ({@link #feedbackType},
 * {@link #message}) are mutable. The service validates the caller
 * is the original feedback author.</p>
 *
 * <p>See {@link CreateTeacherFeedbackRequest} for the discussion
 * of {@link #feedbackType} (the weak-areas tag) and the message
 * length rationale (5-2000 chars).</p>
 */
public record UpdateTeacherFeedbackRequest(
        @Size(max = 30, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = 5, max = 2000, message = "Feedback message must be between 5 and 2000 characters.")
        String message) {
}
