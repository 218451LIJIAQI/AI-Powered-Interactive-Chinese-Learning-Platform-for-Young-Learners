package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Handwriting practice feedback. {@code status} is SUCCESS when the AI provider analysed the
 * image (limited AI-assisted feedback, never a score), otherwise FAILED or UNAVAILABLE; the
 * uploaded image is stored for teacher review in every case as the practice submission
 * {@code submissionId} (D7a). {@code notice} labels the result.
 */
@JsonPropertyOrder({ "feedback", "clarityComment", "improvementTips", "encouragement", "status", "notice", "submissionId" })
public record AiWritingFeedbackResponse(
        String feedback,
        String clarityComment,
        String improvementTips,
        String encouragement,
        String status,
        String notice,
        Long submissionId) {

    public AiWritingFeedbackResponse(
            String feedback,
            String clarityComment,
            String improvementTips,
            String encouragement,
            String status,
            String notice) {
        this(feedback, clarityComment, improvementTips, encouragement, status, notice, null);
    }

    public AiWritingFeedbackResponse withSubmissionId(Long id) {
        return new AiWritingFeedbackResponse(feedback, clarityComment, improvementTips, encouragement, status, notice, id);
    }
}