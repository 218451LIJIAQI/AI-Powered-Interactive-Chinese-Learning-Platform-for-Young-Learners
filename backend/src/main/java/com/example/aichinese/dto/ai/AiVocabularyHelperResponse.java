package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "chineseWord",
        "pinyin",
        "englishMeaning",
        "bahasaMelayuMeaning",
        "simpleExplanation",
        "exampleSentence",
        "exampleSentencePinyin",
        "exampleSentenceTranslation",
        "imagePromptOrDescription",
        "miniPracticeQuestion",
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
        String exampleSentencePinyin,
        String exampleSentenceTranslation,
        String imagePromptOrDescription,
        String miniPracticeQuestion,
        String encouragement,
        String status) {
}
