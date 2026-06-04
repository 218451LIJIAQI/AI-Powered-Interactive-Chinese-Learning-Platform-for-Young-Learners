package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "title", "topic", "level", "category", "usageMode", "questions", "status" })
public record AiQuizGeneratorResponse(
        String title,
        String topic,
        String level,
        String category,
        String usageMode,
        List<AiGeneratedQuizQuestionResponse> questions,
        String status) {

    public AiQuizGeneratorResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
