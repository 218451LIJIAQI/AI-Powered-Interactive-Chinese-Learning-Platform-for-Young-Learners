package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.MandarinLevel;

/**
 * {@code POST /students/level-check/next}: the level the student picked (null = Beginner 1), every answer so far in
 * order, and the server-signed {@code runToken} returned by the previous step (omitted on the first call). An answer's
 * {@code optionId} is null for "I don't know yet".
 */
public record LevelCheckNextRequest(
        MandarinLevel startLevel,

        @Size(max = 8, message = "The level check has at most 8 questions.")
        List<@NotNull(message = "Question is required.") @Valid Answer> answers,

        @Size(max = 512, message = "Start the level check again.")
        String runToken) {

    public LevelCheckNextRequest {
        answers = immutableListOrEmpty(answers);
        runToken = stripToNull(runToken);
    }

    @Override
    public String toString() {
        return "LevelCheckNextRequest[startLevel=" + startLevel
                + ", answers=" + answers.size()
                + ", runToken=" + redactValue(runToken) + "]";
    }

    public record Answer(
            @NotNull(message = "Question is required.")
            @Positive(message = "Question is required.")
            Long questionId,

            @Positive(message = "Answer option id must be positive.")
            Long optionId) {
    }
}
