package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PayoutRequestCreateRequest(
        @NotNull(message = "Payout amount is required.")
        @DecimalMin(value = "0.01", inclusive = true, message = "Payout amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Payout amount must fit within 8 integer digits and 2 decimal places.")
        BigDecimal amount,
        @Size(max = 1000, message = "Payout note must be 1000 characters or fewer.")
        String requestedNote) {
}
