package com.example.aichinese.dto.content;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.ContentStatus;

@JsonPropertyOrder({
        "id",
        "title",
        "description",
        "level",
        "category",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "teacher",
        "createdAt",
        "updatedAt"
})
public record LearningModuleResponse(
        Long id,
        String title,
        String description,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        TeacherOwnerSummaryResponse teacher,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public LearningModuleResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
