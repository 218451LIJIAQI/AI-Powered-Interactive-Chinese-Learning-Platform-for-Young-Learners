package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "questionText",
        "questionType",
        "options",
        "correctAnswer",
        "explanation",
        "pinyin",
        "translation"
})
public record AiGeneratedQuizQuestionResponse(
        String questionText,
        String questionType,
        List<String> options,
        String correctAnswer,
        String explanation,
        String pinyin,
        String translation) {

    public AiGeneratedQuizQuestionResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
