package com.example.aichinese.dto.learn;

import com.example.aichinese.entity.enums.AnswerConfidence;

/**
 * The server's verdict on one answer: the right answer as shown to the student ("你叫什么名字？"), its pinyin and
 * meaning when the item has them, why, and for confidence-rated Practice checks the effective confidence recorded by
 * the server (e.g. {@link AnswerConfidence#GUESS} when an earlier hint applies).
 */
public record AnswerCheckResponse(
        boolean correct,
        String correctAnswer,
        String pinyin,
        String meaning,
        String explanation,
        AnswerConfidence effectiveConfidence) {

    public AnswerCheckResponse(
            boolean correct,
            String correctAnswer,
            String pinyin,
            String meaning,
            String explanation) {
        this(correct, correctAnswer, pinyin, meaning, explanation, null);
    }

    public AnswerCheckResponse withEffectiveConfidence(AnswerConfidence confidence) {
        return new AnswerCheckResponse(correct, correctAnswer, pinyin, meaning, explanation, confidence);
    }
}
