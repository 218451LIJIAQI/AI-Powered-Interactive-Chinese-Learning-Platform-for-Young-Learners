package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "userId",
        "fullName",
        "email",
        "roles",
        "active",
        "teacherStatus",
        "createdAt",
        "updatedAt"
})
public record AdminUserListItemResponse(
        Long userId,
        String fullName,
        String email,
        List<String> roles,
        boolean active,
        TeacherStatus teacherStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AdminUserListItemResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
