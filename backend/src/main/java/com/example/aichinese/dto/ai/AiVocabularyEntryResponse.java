package com.example.aichinese.dto.ai;

public record AiVocabularyEntryResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning) {
}
