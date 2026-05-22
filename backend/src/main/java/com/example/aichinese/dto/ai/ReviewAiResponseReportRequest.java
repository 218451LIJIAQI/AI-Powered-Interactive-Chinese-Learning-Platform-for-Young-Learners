package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewAiResponseReportRequest(
        @NotBlank(message = "Review status is required.")
        @Size(max = 30, message = "Review status must be 30 characters or fewer.")
        String status,

        @NotBlank(message = "Reviewer note is required.")
        @Size(max = 1000, message = "Reviewer note must be 1000 characters or fewer.")
        String reviewerNote) {
}
