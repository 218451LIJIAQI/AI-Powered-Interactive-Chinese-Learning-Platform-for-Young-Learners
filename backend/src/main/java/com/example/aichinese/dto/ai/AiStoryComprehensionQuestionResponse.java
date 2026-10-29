package com.example.aichinese.dto.ai;

import java.util.List;

public record AiStoryComprehensionQuestionResponse(
        String question,
        List<String> options,
        String answer,
        String explanation) {
}
