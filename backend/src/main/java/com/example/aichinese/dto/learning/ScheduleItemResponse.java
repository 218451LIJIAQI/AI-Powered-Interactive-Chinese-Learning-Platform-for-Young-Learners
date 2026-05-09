package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record ScheduleItemResponse(
        String itemType,
        Long itemId,
        String title,
        String description,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String secondaryLabel,
        String routePath,
        boolean actionable) {
}
