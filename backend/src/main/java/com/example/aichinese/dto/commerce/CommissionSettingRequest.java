package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CommissionSettingRequest(
        @NotNull(message = "Commission rate is required.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Commission rate must not be negative.")
        @DecimalMax(value = "1.0", inclusive = true, message = "Commission rate must not exceed 1.0.")
        BigDecimal commissionRate) {
}
