package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTeacherFeedbackRequest(
        @NotNull(message = "Student id is required.")
        @Positive(message = "Student id must be positive.")
        Long studentId,

        @Positive(message = "Lesson id must be positive.")
        Long lessonId,

        @Positive(message = "Quiz attempt id must be positive.")
        Long quizAttemptId,

        @Size(max = 30, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = 5, max = 2000, message = "Feedback message must be between 5 and 2000 characters.")
        String message) {
}
