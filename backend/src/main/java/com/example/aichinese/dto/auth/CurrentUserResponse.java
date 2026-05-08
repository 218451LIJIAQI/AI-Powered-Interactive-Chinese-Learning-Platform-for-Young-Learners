package com.example.aichinese.dto.auth;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        StudentProfileSummary studentProfile,
        TeacherProfileSummary teacherProfile) {

    public record StudentProfileSummary(
            Long id,
            String displayName,
            String currentLevel,
            Integer totalPoints) {
    }

    public record TeacherProfileSummary(
            Long id,
            TeacherStatus teacherStatus,
            String bio,
            String rejectionReason) {
    }
}
