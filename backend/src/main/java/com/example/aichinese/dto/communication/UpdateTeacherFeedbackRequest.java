package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeacherFeedbackRequest(
        @Size(max = MAX_FEEDBACK_TYPE_LENGTH, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = MIN_MESSAGE_LENGTH, max = MAX_MESSAGE_LENGTH,
                message = "Feedback message must be between 5 and 2000 characters.")
        String message) {

    public static final int MAX_FEEDBACK_TYPE_LENGTH =
            CreateTeacherFeedbackRequest.MAX_FEEDBACK_TYPE_LENGTH;

    public static final int MIN_MESSAGE_LENGTH =
            CreateTeacherFeedbackRequest.MIN_MESSAGE_LENGTH;

    public static final int MAX_MESSAGE_LENGTH =
            CreateTeacherFeedbackRequest.MAX_MESSAGE_LENGTH;

    public UpdateTeacherFeedbackRequest {
        feedbackType = stripToNull(feedbackType);
        message = strip(message);
    }
}
