package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "originalSentence",
        "correctedSentence",
        "pinyin",
        "englishMeaning",
        "bahasaMelayuMeaning",
        "improvementExplanation",
        "naturalSentenceVersion",
        "practiceTask",
        "encouragement",
        "status"
})
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
