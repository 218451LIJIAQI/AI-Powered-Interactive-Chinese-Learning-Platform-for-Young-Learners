package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "chineseWord",
        "pinyin",
        "englishMeaning",
        "bahasaMelayuMeaning",
        "simpleExplanation",
        "exampleSentence",
        "exampleSentenceTranslation",
        "encouragement",
        "status"
})
public record AiVocabularyHelperResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning,
        String simpleExplanation,
        String exampleSentence,
        String exampleSentenceTranslation,
        String encouragement,
        String status) {
}
