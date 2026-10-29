package com.example.aichinese.dto.ai;

import java.util.List;

public record AiQuizGeneratorResponse(
        String title,
        String topic,
        List<AiGeneratedQuizQuestionResponse> questions,
        String status) {
}
