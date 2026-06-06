package com.example.aichinese.dto.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

@JsonPropertyOrder({
        "contentType",
        "id",
        "title",
        "description",
        "teacher",
        "level",
        "category",
        "learningType",
        "accessType",
        "price",
        "contentStatus",
        "rejectionReason",
        "createdAt",
        "updatedAt"
})
public record ContentSubmissionResponse(
        ManagedContentType contentType,
        Long id,
        String title,
        String description,
        TeacherOwnerSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        AccessType accessType,
        BigDecimal price,
        ContentStatus contentStatus,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public ContentSubmissionResponse {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(accessType, "accessType must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
