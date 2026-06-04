package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "questionType",
        "questionText",
        "correctAnswerText",
        "points",
        "displayOrder",
        "options",
        "explanation"
})
public record UpdateQuizQuestionRequest(
        @NotBlank(message = "Question type is required.")
        @Size(max = 50, message = "Question type must be 50 characters or fewer.")
        String questionType,
        @NotBlank(message = "Question text is required.")
        @Size(max = 2000, message = "Question text must be 2000 characters or fewer.")
        String questionText,
        @Size(max = 1000, message = "Correct answer text must be 1000 characters or fewer.")
        String correctAnswerText,
        @NotNull(message = "Question points are required.")
        @Positive(message = "Question points must be greater than 0.")
        Integer points,
        @PositiveOrZero(message = "Question display order must be greater than or equal to 0.")
        Integer displayOrder,
        @Size(max = 200, message = "A quiz question cannot contain more than 200 options.")
        @Valid
        List<@NotNull(message = "Quiz option must not be null.") QuizOptionRequest> options,
        @Size(max = 2000, message = "Explanation must be 2000 characters or fewer.")
        String explanation) {

    public UpdateQuizQuestionRequest {
        questionType = strip(questionType);
        questionText = strip(questionText);
        correctAnswerText = stripToNull(correctAnswerText);
        explanation = stripToNull(explanation);
        options = immutableListOrEmpty(options);
    }
}
