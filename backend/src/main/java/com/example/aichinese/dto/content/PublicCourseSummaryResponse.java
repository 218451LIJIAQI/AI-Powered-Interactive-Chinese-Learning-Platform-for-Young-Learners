package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

public record PublicCourseSummaryResponse(
        String id,
        Long contentId,
        ManagedContentType type,
        String title,
        String shortDescription,
        PublicTeacherSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        BigDecimal rating,
        String coverImageUrl,
        boolean locked,
        ContentStatus status) {
}
