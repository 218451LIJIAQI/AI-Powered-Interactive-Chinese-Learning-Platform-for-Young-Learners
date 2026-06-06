package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

@JsonPropertyOrder({
        "id",
        "contentId",
        "type",
        "title",
        "shortDescription",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "currencyCode",
        "coverImageUrl",
        "locked",
        "status"
})
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
        String currencyCode,
        String coverImageUrl,
        boolean locked,
        ContentStatus status) {

    public PublicCourseSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
