package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI writing feedback returned by the writing-feedback
 * endpoint — a longer-form companion to
 * {@link AiSentencePracticeResponse}, scoped to multi-sentence
 * compositions rather than single sentences.
 *
 * <p>Drives the "Get feedback on my writing" surface. {@link #feedback}
 * is the overall assessment; {@link #clarityComment} drills into
 * structure / coherence; {@link #improvementTips} gives
 * actionable next-step advice; {@link #encouragement} is the
 * motivational close.</p>
 *
 * <p>Lighter shape than {@link AiSentencePracticeResponse} — the
 * writing surface focuses on macro feedback rather than per-line
 * corrections, since for longer text per-line corrections
 * overwhelm the learner. {@link #status} mirrors the
 * {@link AiChatResponse#status} convention.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 */
@JsonPropertyOrder({ "feedback", "clarityComment", "improvementTips", "encouragement", "status" })
public record AiWritingFeedbackResponse(
        String feedback,
        String clarityComment,
        String improvementTips,
        String encouragement,
        String status) {
}
