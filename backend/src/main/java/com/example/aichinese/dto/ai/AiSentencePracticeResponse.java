package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI sentence-practice feedback returned by
 * {@code POST /api/v1/ai/sentence-practice}.
 *
 * <h2>Layered correction model</h2>
 * <p>Returns three sentence variants so the student can compare
 * against their original at a glance:</p>
 * <ul>
 *   <li>{@link #originalSentence} — echoed back so the front-end
 *       can render side-by-side without trusting its local copy.</li>
 *   <li>{@link #correctedSentence} — the minimal-edit corrected
 *       version (preserves the student's voice).</li>
 *   <li>{@link #naturalSentenceVersion} — the most-natural native
 *       phrasing (may diverge significantly).</li>
 * </ul>
 *
 * <p>{@link #improvementExplanation} explains the differences in
 * the student's preferred language. {@link #practiceTask} is a
 * follow-up exercise (e.g. "Now try writing a similar sentence
 * about the weather"). {@link #encouragement} is the
 * motivational close.</p>
 *
 * <p>{@link #pinyin}, {@link #englishMeaning},
 * {@link #bahasaMelayuMeaning} apply to the
 * {@link #correctedSentence} — the canonical form the student
 * should aim for. See
 * {@link AiVocabularyHelperResponse} for the same dual-language
 * pattern.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 */
@JsonPropertyOrder({
        "originalSentence",
        "correctedSentence",
        "pinyin",
        "englishMeaning",
        "bahasaMelayuMeaning",
        "improvementExplanation",
        "naturalSentenceVersion",
        "practiceTask",
        "encouragement",
        "status"
})
public record AiSentencePracticeResponse(
        String originalSentence,
        String correctedSentence,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning,
        String improvementExplanation,
        String naturalSentenceVersion,
        String practiceTask,
        String encouragement,
        String status) {
}
