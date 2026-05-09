package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;

public record UpdateLiveClassStatusRequest(
        @NotBlank(message = "Status is required.")
        String status) {
}
