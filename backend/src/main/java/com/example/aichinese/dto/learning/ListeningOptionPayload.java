package com.example.aichinese.dto.learning;

public record ListeningOptionPayload(
        String optionText,
        boolean correctOption,
        Integer displayOrder) {
}
