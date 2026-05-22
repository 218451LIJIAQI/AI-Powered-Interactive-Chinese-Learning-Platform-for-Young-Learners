package com.example.aichinese.dto.ai;

public record AiStoryVocabularyItemResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning) {
}
