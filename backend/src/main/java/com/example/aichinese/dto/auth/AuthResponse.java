package com.example.aichinese.dto.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({ "token", "user" })
public record AuthResponse(
        String token,
        UserSummary user) {

    public AuthResponse {
        token = requireNonBlank(token, "token");
        Objects.requireNonNull(user, "user must not be null");
    }

    @Override
    public String toString() {
        return "AuthResponse[token=***REDACTED***, user=" + user + "]";
    }

    @JsonPropertyOrder({ "id", "fullName", "email", "roles", "teacherStatus" })
    public record UserSummary(
            Long id,
            String fullName,
            String email,
            List<String> roles,
            TeacherStatus teacherStatus) {

        public UserSummary {
            Objects.requireNonNull(id, "id must not be null");
            fullName = requireNonBlank(fullName, "fullName");
            email = requireNonBlank(email, "email");
            roles = normalizeRoles(roles);
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
            String stripped = requireNonBlank(role, "role");
            normalized.add(stripped);
        }
        return List.copyOf(normalized);
    }
}
