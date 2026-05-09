package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeacherFeedbackRequest(
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = 5, max = 2000, message = "Feedback message must be between 5 and 2000 characters.")
        String message) {
}
