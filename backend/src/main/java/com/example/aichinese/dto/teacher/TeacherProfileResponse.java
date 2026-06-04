package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "userId",
        "fullName",
        "email",
        "teacherProfileId",
        "bio",
        "shortIntroduction",
        "teachingCategories",
        "teacherStatus",
        "rejectionReason",
        "verificationDocuments",
        "createdAt",
        "updatedAt"
})
public record TeacherProfileResponse(
        Long userId,
        String fullName,
        String email,
        Long teacherProfileId,
        String bio,
        String shortIntroduction,
        List<String> teachingCategories,
        TeacherStatus teacherStatus,
        String rejectionReason,
        List<TeacherVerificationDocumentSummaryResponse> verificationDocuments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TeacherProfileResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(teacherProfileId, "teacherProfileId must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        teachingCategories = teachingCategories == null
                ? List.of()
                : List.copyOf(teachingCategories);
        verificationDocuments = verificationDocuments == null
                ? List.of()
                : List.copyOf(verificationDocuments);
    }
}
