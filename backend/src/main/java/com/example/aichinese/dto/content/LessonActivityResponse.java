package com.example.aichinese.dto.content;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LessonActivityType;

public record LessonActivityResponse(
        Long id,
        Long lessonId,
        String title,
        LessonActivityType activityType,
        String instruction,
        String description,
        Integer displayOrder,
        String placeholderContent,
        ContentStatus contentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
