package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.example.aichinese.entity.enums.LearningStage;

/** A teacher's quiz fields, for creating and for updating it. */
public record QuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes,
        LearningStage learningStage) {

    public QuizRequest {
        title = strip(title);
        description = stripToNull(description);
    }
}
