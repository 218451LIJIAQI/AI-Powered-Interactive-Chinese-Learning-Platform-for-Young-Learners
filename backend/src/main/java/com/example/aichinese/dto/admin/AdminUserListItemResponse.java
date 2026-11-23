package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.RoleName;

/** One row of the admin Users list. */
public record AdminUserListItemResponse(
        Long id,
        String fullName,
        String email,
        RoleName role,
        boolean active,
        LocalDateTime joinedAt) {

    @Override
    public String toString() {
        return "AdminUserListItemResponse[id=" + id
                + ", fullName=" + redactValue(fullName)
                + ", email=" + redactValue(email)
                + ", role=" + role
                + ", active=" + active
                + ", joinedAt=" + joinedAt + "]";
    }
}
