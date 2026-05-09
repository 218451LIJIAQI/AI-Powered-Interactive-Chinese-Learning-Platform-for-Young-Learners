package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotBlank(message = "Target type is required.")
        String targetType,

        Long targetId,

        @NotBlank(message = "Reason is required.")
        @Size(min = 10, max = 2000, message = "Reason must be between 10 and 2000 characters.")
        String reason) {
}
