package com.example.aichinese.dto.student.activity;

import java.util.Objects;

public record StudentStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {

    public StudentStoryQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(optionsPayload, "optionsPayload must not be null");
    }
}
