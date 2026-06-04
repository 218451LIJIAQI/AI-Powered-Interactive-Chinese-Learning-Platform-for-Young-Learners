package com.example.aichinese.dto.admin.activity;

public record AdminStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {
}
