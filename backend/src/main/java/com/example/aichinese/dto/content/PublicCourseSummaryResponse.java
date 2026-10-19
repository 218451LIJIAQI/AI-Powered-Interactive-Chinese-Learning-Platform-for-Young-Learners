package com.example.aichinese.dto.content;

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
        "shortDescription",
        "teacher",
        "level",
        "category",
        "learningType",
        "coverImageUrl",
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
        String coverImageUrl,
        ContentStatus status) {

    public PublicCourseSummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
