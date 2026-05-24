package com.example.aichinese.dto.ai;

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
 */
public record AiStoryVocabularyItemResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning) {
}
