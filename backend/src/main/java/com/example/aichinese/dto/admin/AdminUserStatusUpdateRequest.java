package com.example.aichinese.dto.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder({ "active" })
public record AdminUserStatusUpdateRequest(
        @NotNull(message = "Active flag is required.")
        Boolean active) {
}
