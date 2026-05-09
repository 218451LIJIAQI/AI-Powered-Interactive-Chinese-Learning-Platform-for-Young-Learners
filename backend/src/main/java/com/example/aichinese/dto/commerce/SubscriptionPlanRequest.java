package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriptionPlanRequest(
        @NotBlank(message = "Plan name is required.")
        @Size(max = 120, message = "Plan name must be 120 characters or fewer.")
        String name,
        @NotNull(message = "Plan price is required.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Plan price must be greater than or equal to 0.")
        BigDecimal price,
        @NotBlank(message = "Benefits are required.")
        @Size(max = 4000, message = "Benefits must be 4000 characters or fewer.")
        String benefits,
        Boolean active) {
}
