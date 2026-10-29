package com.example.aichinese.dto.learn;

/**
 * The server's verdict on one answer: the right answer as shown to the student ("你叫什么名字？"), its pinyin and
 * meaning when the item has them, and why.
 */
public record AnswerCheckResponse(
        boolean correct,
        String correctAnswer,
        String pinyin,
        String meaning,
        String explanation) {
}
