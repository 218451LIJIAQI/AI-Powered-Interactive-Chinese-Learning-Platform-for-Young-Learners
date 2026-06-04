package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "studentId",
        "userId",
        "fullName",
        "email",
        "preferredLanguage",
        "createdAt"
})
public record AdminStudentListItemResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String preferredLanguage,
        LocalDateTime createdAt) {

    public AdminStudentListItemResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
