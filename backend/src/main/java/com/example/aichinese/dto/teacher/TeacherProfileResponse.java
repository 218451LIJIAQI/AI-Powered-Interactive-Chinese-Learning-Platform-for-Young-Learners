package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

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
}
