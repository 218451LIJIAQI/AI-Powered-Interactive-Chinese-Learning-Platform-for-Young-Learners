package com.example.aichinese.dto.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "id",
        "fullName",
        "email",
        "preferredLanguage",
        "roles",
        "studentProfile",
        "teacherProfile"
})
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

    /** {@code institution} is optional (university, college or workplace). */
    @JsonPropertyOrder({ "id", "displayName", "currentLevel", "totalPoints", "institution" })
    public record StudentProfileSummary(
            Long id,
            String displayName,
            String currentLevel,
            Integer totalPoints,
            String institution) {

        public StudentProfileSummary {
            Objects.requireNonNull(id, "id must not be null");
            displayName = normalizeOptionalText(displayName);
            currentLevel = normalizeOptionalText(currentLevel);
            institution = normalizeOptionalText(institution);
        }

        public StudentProfileSummary(Long id, String displayName, String currentLevel, Integer totalPoints) {
            this(id, displayName, currentLevel, totalPoints, null);
        }
    }

    @JsonPropertyOrder({ "id", "teacherStatus", "bio", "rejectionReason" })
    public record TeacherProfileSummary(
            Long id,
            TeacherStatus teacherStatus,
            String bio,
            String rejectionReason) {

        public TeacherProfileSummary {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
            bio = normalizeOptionalText(bio);
            rejectionReason = normalizeOptionalText(rejectionReason);
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        String stripped = Objects.requireNonNull(value, fieldName + " must not be null").strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return stripped;
    }

    private static List<String> normalizeRoles(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>(source.size());
        for (String role : source) {
            normalized.add(requireNonBlank(role, "role"));
        }
        return List.copyOf(normalized);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
