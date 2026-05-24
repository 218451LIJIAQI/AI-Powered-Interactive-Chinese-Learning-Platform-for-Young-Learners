package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI-generated story bundle returned by
 * {@code POST /api/v1/ai/story-generator}.
 *
 * <p>Aggregate response: a story body, three multilingual
 * versions, a vocabulary list, and comprehension questions —
 * everything the student needs to read, understand, and self-
 * test on the generated story without follow-up calls.</p>
 *
 * <h2>Story body in three forms</h2>
 * <ul>
 *   <li>{@link #chineseStory} — the canonical Chinese story.</li>
 *   <li>{@link #pinyin} — full pinyin transcription, parallel to
 *       the Chinese.</li>
 *   <li>{@link #englishTranslation} /
 *       {@link #bahasaMelayuTranslation} — full translations so
 *       the student can compare against the Chinese (the
 *       front-end shows whichever matches the user's preferred
 *       study language).</li>
 * </ul>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #vocabularyList} — key words from the story with
 *       multilingual scaffolding via
 *       {@link AiStoryVocabularyItemResponse}.</li>
 *   <li>{@link #comprehensionQuestions} — multiple-choice
 *       questions with answers and explanations via
 *       {@link AiStoryComprehensionQuestionResponse}.</li>
 * </ul>
 *
 * <p>{@link #usageMode} is reserved for future teacher / student
 * branching parallel to
 * {@link AiQuizGeneratorResponse#usageMode}.
 * {@link #encouragement} is the motivational close;
 * {@link #status} mirrors the {@link AiChatResponse#status}
 * convention.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Defensive immutability</h2>
 * <p>The canonical constructor wraps both embedded lists in
 * {@link List#copyOf(java.util.Collection) List.copyOf} so the
 * service-layer {@code ArrayList}s that build the vocabulary and
 * comprehension pools cannot leak through and be mutated after
 * the response is built. {@code null} inputs are normalised to
 * empty lists so the wire format always emits {@code []} rather
 * than {@code null} and the front-end never has to branch on
 * {@code null}.</p>
 */
@JsonPropertyOrder({
        "title",
        "chineseStory",
        "pinyin",
        "englishTranslation",
        "bahasaMelayuTranslation",
        "vocabularyList",
        "comprehensionQuestions",
        "encouragement",
        "usageMode",
        "status"
})
public record AiStoryGeneratorResponse(
        String title,
        String chineseStory,
        String pinyin,
        String englishTranslation,
        String bahasaMelayuTranslation,
        List<AiStoryVocabularyItemResponse> vocabularyList,
        List<AiStoryComprehensionQuestionResponse> comprehensionQuestions,
        String encouragement,
        String usageMode,
        String status) {

    /**
     * Defensive canonical constructor: wraps both embedded lists
     * in immutable copies so external mutation of the originally
     * supplied lists cannot leak into the already-serialised
     * response. {@code null} inputs are normalised to
     * {@link List#of()}.
     */
    public AiStoryGeneratorResponse {
        vocabularyList = vocabularyList == null ? List.of() : List.copyOf(vocabularyList);
        comprehensionQuestions = comprehensionQuestions == null
                ? List.of()
                : List.copyOf(comprehensionQuestions);
    }
}
