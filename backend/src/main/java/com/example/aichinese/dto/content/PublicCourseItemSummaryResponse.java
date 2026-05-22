package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.ManagedContentType;

public record PublicCourseItemSummaryResponse(
        ManagedContentType type,
        Long contentId,
        String title,
        Integer displayOrder) {
}
