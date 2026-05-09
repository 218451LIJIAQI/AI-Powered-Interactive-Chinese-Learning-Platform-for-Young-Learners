package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayoutDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = 1000, message = "Decision reason must be 1000 characters or fewer.")
        String reason) {
}
