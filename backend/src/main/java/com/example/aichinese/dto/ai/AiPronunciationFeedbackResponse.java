package com.example.aichinese.dto.ai;

/**
 * Structured AI pronunciation feedback returned by the
 * pronunciation-analysis endpoint (typically a multipart
 * {@code POST} with an audio upload). The audio pipeline is
 * gated by
 * {@link AiAvailabilityResponse#audioAnalysisAvailable()} —
 * front-end hides this surface when the capability is offline.
 *
 * <p>Drives the "Read this aloud" practice card. Mandarin's tonal
 * nature makes pronunciation feedback critical — getting the
 * tone wrong changes the meaning of the word. The AI returns
 * separate {@link #pronunciationComment} (consonants/vowels
 * shape) and {@link #toneComment} (tone-mark accuracy) feedback
 * so the student can target the right kind of practice.</p>
 *
 * <p>{@link #feedback} is the headline summary; the breakdown
 * fields go deeper. {@link #improvementTips} is actionable
 * advice; {@link #encouragement} is the motivational close.
 * Every field is plain text in the student's preferred
 * language.</p>
 */
public record AiPronunciationFeedbackResponse(
        String feedback,
        String pronunciationComment,
        String toneComment,
        String improvementTips,
        String encouragement,
        String status) {
}
