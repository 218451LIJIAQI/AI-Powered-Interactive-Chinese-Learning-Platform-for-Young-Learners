package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.example.aichinese.entity.enums.LearningStage;

@JsonPropertyOrder({ "title", "description", "timeLimitMinutes" })
public record UpdateQuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes,
        LearningStage learningStage) {

    public UpdateQuizRequest {
        title = strip(title);
        description = stripToNull(description);
    }
}
