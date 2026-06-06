package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "teacherId",
        "userId",
        "fullName",
        "email",
        "teacherStatus",
        "bioSummary",
        "verificationDocumentCount",
        "latestReviewDate",
        "latestSubmissionTime",
        "createdAt"
})
public record AdminTeacherListItemResponse(
        Long teacherId,
        Long userId,
        String fullName,
        String email,
        TeacherStatus teacherStatus,
        String bioSummary,
        long verificationDocumentCount,
        LocalDateTime latestReviewDate,
        LocalDateTime latestSubmissionTime,
        LocalDateTime createdAt) {

    public AdminTeacherListItemResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        verificationDocumentCount = Math.max(verificationDocumentCount, 0L);
    }
}
