package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "chineseWord", "pinyin", "englishMeaning", "bahasaMelayuMeaning" })
public record AiStoryVocabularyItemResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning) {
}
