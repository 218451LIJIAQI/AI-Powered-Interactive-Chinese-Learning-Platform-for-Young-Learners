package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "id", "optionText", "correctOption", "displayOrder" })
public record TeacherQuizOptionResponse(
        Long id,
        String optionText,
        boolean correctOption,
        Integer displayOrder) {

    public TeacherQuizOptionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(optionText, "optionText must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
