package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "id", "optionText", "displayOrder" })
public record QuizOptionResponse(
        Long id,
        String optionText,
        Integer displayOrder) {

    public QuizOptionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(optionText, "optionText must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
