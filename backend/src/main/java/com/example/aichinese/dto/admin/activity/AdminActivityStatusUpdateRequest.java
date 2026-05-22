package com.example.aichinese.dto.admin.activity;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code PATCH /admin/activities/.../status} — admins use the same
 * endpoint to publish, unpublish or archive an activity by sending the
 * desired terminal {@link ContentStatus}.
 */
public record AdminActivityStatusUpdateRequest(
        @NotNull(message = "Target status is required.")
        ContentStatus status) {
}
