package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;

public record MatchingGameResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        ContentStatus contentStatus,
        List<MatchingGameItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
