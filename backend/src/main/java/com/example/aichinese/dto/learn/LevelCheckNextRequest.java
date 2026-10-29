package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.MandarinLevel;

/**
 * {@code POST /students/level-check/next}: the level the student picked (null = Beginner 1) and every answer so far,
 * in order. An answer's {@code optionId} is null for "I don't know yet".
 */
public record LevelCheckNextRequest(
        MandarinLevel startLevel,

        @Size(max = 6, message = "The level check has at most 6 questions.")
        List<@Valid Answer> answers) {

    public LevelCheckNextRequest {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    public record Answer(
            @NotNull(message = "Question is required.")
            Long questionId,

            Long optionId) {
    }
}
