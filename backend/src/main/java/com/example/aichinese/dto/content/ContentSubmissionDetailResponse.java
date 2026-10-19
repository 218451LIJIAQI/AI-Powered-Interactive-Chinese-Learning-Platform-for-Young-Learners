package com.example.aichinese.dto.content;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

@JsonPropertyOrder({
        "contentType",
        "id",
        "title",
        "description",
        "basicContent",
        "moduleId",
        "moduleTitle",
        "teacher",
        "level",
        "category",
        "learningType",
        "coverImageUrl",
        "contentStatus",
        "rejectionReason",
        "packageItems",
        "createdAt",
        "updatedAt"
})
public record ContentSubmissionDetailResponse(
        ManagedContentType contentType,
        Long id,
        String title,
        String description,
        String basicContent,
        Long moduleId,
        String moduleTitle,
        TeacherOwnerSummaryResponse teacher,
        LearningLevelResponse level,
        LearningCategoryResponse category,
        LearningType learningType,
        String coverImageUrl,
        ContentStatus contentStatus,
        String rejectionReason,
        List<PackageItemResponse> packageItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public ContentSubmissionDetailResponse {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }
}
