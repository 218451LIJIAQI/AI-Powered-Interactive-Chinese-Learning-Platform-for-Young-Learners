package com.example.aichinese.dto.ai;

public record AiWritingFeedbackResponse(
        String feedback,
        String clarityComment,
        String improvementTips,
        String encouragement,
        String status) {
}
