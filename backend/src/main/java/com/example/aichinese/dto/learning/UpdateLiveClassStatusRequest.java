package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLiveClassStatusRequest(
        @NotBlank(message = "Status is required.")
        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status) {
}
