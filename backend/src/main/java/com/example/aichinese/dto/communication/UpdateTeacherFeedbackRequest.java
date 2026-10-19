package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeacherFeedbackRequest(
        @Size(max = CreateTeacherFeedbackRequest.MAX_FEEDBACK_TYPE_LENGTH, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = CreateTeacherFeedbackRequest.MIN_MESSAGE_LENGTH, max = CreateTeacherFeedbackRequest.MAX_MESSAGE_LENGTH,
                message = "Feedback message must be between 5 and 2000 characters.")
        String message) {

    public UpdateTeacherFeedbackRequest {
        feedbackType = stripToNull(feedbackType);
        message = strip(message);
    }
}
