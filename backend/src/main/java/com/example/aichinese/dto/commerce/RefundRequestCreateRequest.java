package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotNull(message = "Order id is required.")
        Long orderId,
        @NotBlank(message = "Refund reason is required.")
        @Size(max = 1000, message = "Refund reason must be 1000 characters or fewer.")
        String reason) {
}
