package com.example.aichinese.dto.review;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Review detail: the queue row plus the type-specific body the reviewer needs
 * (ContentSubmissionDetailResponse for courses/modules/lessons, TeacherQuizResponse for quizzes,
 * the Admin*Response of the activity type for practice activities).
 */
@JsonPropertyOrder({ "item", "detail" })
public record ContentReviewDetailResponse(ContentReviewItemResponse item, Object detail) {

    public ContentReviewDetailResponse {
        Objects.requireNonNull(item, "item must not be null");
    }
}