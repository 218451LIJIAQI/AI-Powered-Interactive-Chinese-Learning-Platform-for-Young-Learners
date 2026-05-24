package com.example.aichinese.dto.ai;

import java.util.List;

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
 */
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
}
