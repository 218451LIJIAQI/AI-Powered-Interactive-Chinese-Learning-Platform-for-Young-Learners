package com.example.aichinese.dto.ai;

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
