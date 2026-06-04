package com.example.aichinese.dto.student.activity;

import java.util.Objects;

public record StudentMatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer sortOrder) {

    public StudentMatchingGameItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(leftText, "leftText must not be null");
        Objects.requireNonNull(rightText, "rightText must not be null");
    }
}
