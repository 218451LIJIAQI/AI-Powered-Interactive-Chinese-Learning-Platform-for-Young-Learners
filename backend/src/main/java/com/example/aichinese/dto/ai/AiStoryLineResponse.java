package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

/**
 * Chinese with its pinyin and its translation into the student's language: a sentence of a story, or a line (or, for
 * a word, its meaning) in a talk with Xiaomei.
 */
public record AiStoryLineResponse(String chinese, String pinyin, String translation) {

    @Override
    public String toString() {
        return "AiStoryLineResponse[chinese=" + redactLength(chinese)
                + ", pinyin=" + redactLength(pinyin)
                + ", translation=" + redactLength(translation) + "]";
    }
}
