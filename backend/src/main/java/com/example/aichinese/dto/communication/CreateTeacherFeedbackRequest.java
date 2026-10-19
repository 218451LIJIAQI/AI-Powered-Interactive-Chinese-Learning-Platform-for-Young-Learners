package com.example.aichinese.dto.communication;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

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
        @Positive(message = "Practice submission id must be positive.")
        Long practiceSubmissionId,

        @Size(max = MAX_FEEDBACK_TYPE_LENGTH, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = MIN_MESSAGE_LENGTH, max = MAX_MESSAGE_LENGTH,
                message = "Feedback message must be between 5 and 2000 characters.")
        String message) {

    public static final int MAX_FEEDBACK_TYPE_LENGTH = 30;

    public static final int MIN_MESSAGE_LENGTH = 5;

    public static final int MAX_MESSAGE_LENGTH = 2000;

    public CreateTeacherFeedbackRequest {
        feedbackType = stripToNull(feedbackType);
        message = strip(message);
    }
}
