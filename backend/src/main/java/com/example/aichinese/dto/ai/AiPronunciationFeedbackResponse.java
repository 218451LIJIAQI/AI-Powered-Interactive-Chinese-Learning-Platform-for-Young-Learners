package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Pronunciation practice response. The configured model does not accept audio, so
 * {@code status} is UNAVAILABLE and the recording is stored for teacher review as the practice
 * submission {@code submissionId} (D7a); {@code notice} labels the result as limited practice
 * feedback, never a score.
 */
@JsonPropertyOrder({
        "feedback",
        "pronunciationComment",
        "toneComment",
        "improvementTips",
        "encouragement",
        "status",
        "notice",
        "submissionId"
})
public record AiPronunciationFeedbackResponse(
        String feedback,
        String pronunciationComment,
        String toneComment,
        String improvementTips,
        String encouragement,
        String status,
        String notice,
        Long submissionId) {
}