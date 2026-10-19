package com.example.aichinese.dto.content;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;

@JsonPropertyOrder({
        "id",
        "contentId",
        "type",
        "title",
        "description",
        "teacher",
        "level",
        "category",
        "learningType",
        "coverImageUrl",
        "status",
        "basicContent",
        "packageItems",
        "callToAction"
})
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
        String coverImageUrl,
        ContentStatus status,
        String basicContent,
        List<PublicCourseItemSummaryResponse> packageItems,
        String callToAction) {

    public PublicCourseDetailResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(callToAction, "callToAction must not be null");
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }
}
