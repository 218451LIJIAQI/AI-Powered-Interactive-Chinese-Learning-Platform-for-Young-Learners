package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTeacherFeedbackRequest(
        @NotNull(message = "Student id is required.")
        Long studentId,

        Long lessonId,

        Long quizAttemptId,

        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = 5, max = 2000, message = "Feedback message must be between 5 and 2000 characters.")
        String message) {
}
