package com.example.aichinese.dto.ai;

import java.util.List;

public record AiGeneratedQuizQuestionResponse(
        String questionText,
        String questionType,
        List<String> options,
        String correctAnswer,
        String explanation,
        String pinyin,
        String translation) {
}
