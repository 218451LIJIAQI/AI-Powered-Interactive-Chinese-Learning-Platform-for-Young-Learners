package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.util.List;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

public record PublicCourseDetailResponse(
        String id,
        Long contentId,
        ManagedContentType type,
        String title,
        String description,
        PublicTeacherSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        String coverImageUrl,
        boolean locked,
        ContentStatus status,
        String basicContent,
        List<PublicCourseItemSummaryResponse> packageItems,
        List<PublicActivitySummaryResponse> activities,
        String callToAction,
        String lockedMessage) {
}
