package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record SubmitQuizAttemptRequest(
        @Valid
        @NotEmpty(message = "At least one answer is required.")
        List<QuizSubmissionAnswerRequest> answers) {
}
