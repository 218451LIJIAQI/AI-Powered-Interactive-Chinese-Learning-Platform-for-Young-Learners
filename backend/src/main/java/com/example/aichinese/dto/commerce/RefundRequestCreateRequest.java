package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotNull(message = "Order id is required.")
        @Positive(message = "Order id must be positive.")
        Long orderId,
        @NotBlank(message = "Refund reason is required.")
        @Size(max = 1000, message = "Refund reason must be 1000 characters or fewer.")
        String reason) {
}
