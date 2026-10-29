package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.Objects;

import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.enums.RoleName;

/** One row of the admin Users list. */
public record AdminUserListItemResponse(
        Long id,
        String fullName,
        String email,
        RoleName role,
        boolean active,
        /** Teachers only. */
        TeacherVerificationState verificationState,
        LocalDateTime joinedAt) {

    public AdminUserListItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
