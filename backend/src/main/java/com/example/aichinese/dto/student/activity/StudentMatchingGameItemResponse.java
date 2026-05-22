package com.example.aichinese.dto.student.activity;

public record StudentMatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer sortOrder) {
}
