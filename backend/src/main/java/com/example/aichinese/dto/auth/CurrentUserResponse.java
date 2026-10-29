package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.normalizeRoles;
import static com.example.aichinese.dto.DtoNormalization.requireNonBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.enums.TeacherStatus;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        StudentProfileSummary studentProfile,
        TeacherProfileSummary teacherProfile) {

    public CurrentUserResponse {
        Objects.requireNonNull(id, "id must not be null");
        fullName = requireNonBlank(fullName, "fullName");
        email = requireNonBlank(email, "email");
        preferredLanguage = requireNonBlank(preferredLanguage, "preferredLanguage");
        roles = normalizeRoles(roles);
    }

    public record StudentProfileSummary(Integer totalPoints) {
    }

    /** The teacher status and, derived from it, where the application stands and when it was approved. */
    public record TeacherProfileSummary(
            TeacherStatus teacherStatus,
            TeacherVerificationState verificationState,
            LocalDateTime approvedAt) {

        public TeacherProfileSummary {
            Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
            Objects.requireNonNull(verificationState, "verificationState must not be null");
        }
    }
}
