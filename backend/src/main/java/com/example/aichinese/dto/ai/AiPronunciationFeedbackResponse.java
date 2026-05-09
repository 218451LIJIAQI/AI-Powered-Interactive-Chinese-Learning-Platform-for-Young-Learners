package com.example.aichinese.dto.ai;

public record AiPronunciationFeedbackResponse(
        String feedback,
        String pronunciationComment,
        String toneComment,
        String improvementTips,
        String encouragement,
        String status) {
}
