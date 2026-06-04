package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "feedback",
        "pronunciationComment",
        "toneComment",
        "improvementTips",
        "encouragement",
        "status"
})
public record AiPronunciationFeedbackResponse(
        String feedback,
        String pronunciationComment,
        String toneComment,
        String improvementTips,
        String encouragement,
        String status) {
}
