package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.TeacherStatus;

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
}
