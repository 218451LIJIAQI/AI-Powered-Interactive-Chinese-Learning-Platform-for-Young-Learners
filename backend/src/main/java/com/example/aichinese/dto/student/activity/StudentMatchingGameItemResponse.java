package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/** Left-hand item of a matching game; its partner is NOT included (no answer key before an attempt). */
public record StudentMatchingGameItemResponse(
        Long id,
        String text,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {

    public StudentMatchingGameItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}