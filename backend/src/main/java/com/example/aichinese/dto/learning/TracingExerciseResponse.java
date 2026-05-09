package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.ContentStatus;

public record TracingExerciseResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String targetCharacter,
        String pinyin,
        String meaning,
        String worksheetUrl,
        String instructions,
        ContentStatus contentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
