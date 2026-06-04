package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "question", "options", "answer", "explanation" })
public record AiStoryComprehensionQuestionResponse(
        String question,
        List<String> options,
        String answer,
        String explanation) {

    public AiStoryComprehensionQuestionResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
