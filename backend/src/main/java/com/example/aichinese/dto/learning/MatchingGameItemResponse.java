package com.example.aichinese.dto.learning;

public record MatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer displayOrder) {
}
