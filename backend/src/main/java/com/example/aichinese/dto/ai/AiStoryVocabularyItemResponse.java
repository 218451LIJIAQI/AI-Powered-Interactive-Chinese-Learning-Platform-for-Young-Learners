package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One vocabulary entry inside
 * {@link AiStoryGeneratorResponse#vocabularyList()}.
 *
 * <p>Shares the same multilingual-meaning shape as
 * {@link AiVocabularyHelperResponse} (Chinese + pinyin + both
 * English and Bahasa Melayu meanings) but trimmed to just the
 * dictionary fields — story-context vocabulary does not need
 * the example sentence / mini-practice / image-prompt extras
 * since the surrounding story already provides usage context.</p>
 *
 * <p>Used to render the "New words in this story" panel
 * alongside the story body.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 */
@JsonPropertyOrder({ "chineseWord", "pinyin", "englishMeaning", "bahasaMelayuMeaning" })
public record AiStoryVocabularyItemResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning) {
}
