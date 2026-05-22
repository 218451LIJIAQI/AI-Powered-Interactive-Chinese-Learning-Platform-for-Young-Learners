package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusUpdateRequest(
        @NotNull(message = "Active flag is required.")
        Boolean active) {
}
