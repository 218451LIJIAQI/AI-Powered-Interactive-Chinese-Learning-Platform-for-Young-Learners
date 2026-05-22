package com.example.aichinese.dto.student.activity;

public record StudentStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {
}
