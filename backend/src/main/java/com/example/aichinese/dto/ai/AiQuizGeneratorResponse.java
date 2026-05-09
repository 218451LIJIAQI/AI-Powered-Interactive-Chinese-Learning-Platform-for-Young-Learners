package com.example.aichinese.dto.ai;

import java.util.List;

public record AiQuizGeneratorResponse(
        String title,
        String topic,
        String level,
        String category,
        String usageMode,
        List<AiGeneratedQuizQuestionResponse> questions,
        String status) {
}
