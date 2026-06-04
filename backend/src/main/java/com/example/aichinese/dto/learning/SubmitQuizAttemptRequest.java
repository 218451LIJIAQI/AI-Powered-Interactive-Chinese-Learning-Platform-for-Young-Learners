package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "answers" })
public record SubmitQuizAttemptRequest(
        @Size(max = 200, message = "A quiz attempt cannot contain more than 200 answers.")
        @Valid
        @NotEmpty(message = "At least one answer is required.")
        List<@NotNull(message = "Quiz answer must not be null.") QuizSubmissionAnswerRequest> answers) {

    public SubmitQuizAttemptRequest {
        answers = immutableListOrEmpty(answers);
    }
}
