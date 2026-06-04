package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "feedback", "clarityComment", "improvementTips", "encouragement", "status" })
public record AiWritingFeedbackResponse(
        String feedback,
        String clarityComment,
        String improvementTips,
        String encouragement,
        String status) {
}
