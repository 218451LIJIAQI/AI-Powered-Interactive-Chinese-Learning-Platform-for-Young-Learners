package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.LessonActivityType;

public record PublicActivitySummaryResponse(
        Long id,
        String title,
        LessonActivityType activityType,
        String instruction,
        Integer displayOrder) {
}
