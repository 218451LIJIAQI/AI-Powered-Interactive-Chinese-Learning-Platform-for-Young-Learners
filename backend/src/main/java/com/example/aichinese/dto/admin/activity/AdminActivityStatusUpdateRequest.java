package com.example.aichinese.dto.admin.activity;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.validation.constraints.NotNull;

public record AdminActivityStatusUpdateRequest(
        @NotNull(message = "Target status is required.")
        ContentStatus status) {
}
