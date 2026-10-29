package com.example.aichinese.dto.ai;

/** One sentence of a story, with its pinyin and its translation into the student's language. */
public record AiStoryLineResponse(String chinese, String pinyin, String translation) {
}
