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
        "teacherStatus"
})
public record RegistrationResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        TeacherStatus teacherStatus) {

    public RegistrationResponse {
        Objects.requireNonNull(id, "id must not be null");
        fullName = requireNonBlank(fullName, "fullName");
        email = requireNonBlank(email, "email");
        preferredLanguage = requireNonBlank(preferredLanguage, "preferredLanguage");
        roles = normalizeRoles(roles);
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
}
