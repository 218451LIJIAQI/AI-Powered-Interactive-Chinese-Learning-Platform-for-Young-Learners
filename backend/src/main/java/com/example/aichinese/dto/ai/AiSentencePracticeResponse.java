package com.example.aichinese.dto.ai;

public record AiSentencePracticeResponse(
        String originalSentence,
        String correctedSentence,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning,
        String improvementExplanation,
        String naturalSentenceVersion,
        String practiceTask,
        String encouragement,
        String status) {
}
