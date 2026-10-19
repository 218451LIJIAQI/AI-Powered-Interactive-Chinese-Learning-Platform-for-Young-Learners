package com.example.aichinese.dto.admin.activity;

/** Story question for authors and reviewers, including the 0-based correct option (never sent to students). */
public record AdminStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder,
        Integer correctOptionIndex) {
}
