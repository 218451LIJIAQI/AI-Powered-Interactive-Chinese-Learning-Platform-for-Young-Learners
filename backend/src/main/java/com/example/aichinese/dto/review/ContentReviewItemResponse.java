package com.example.aichinese.dto.review;

import java.time.LocalDateTime;
import java.util.Objects;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One row of the admin content-review queue (D4). */
@JsonPropertyOrder({
        "type",
        "id",
        "title",
        "contentStatus",
        "rejectionReason",
        "learningStage",
        "ownerUserId",
        "ownerName",
        "ownerTeacherStatus",
        "parentTitle",
        "createdAt",
        "updatedAt"
})
public record ContentReviewItemResponse(
        ReviewContentType type,
        Long id,
        String title,
        ContentStatus contentStatus,
        String rejectionReason,
        LearningStage learningStage,
        Long ownerUserId,
        String ownerName,
        TeacherStatus ownerTeacherStatus,
        String parentTitle,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public ContentReviewItemResponse {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        title = title == null || title.isBlank() ? "(untitled)" : title;
    }
}