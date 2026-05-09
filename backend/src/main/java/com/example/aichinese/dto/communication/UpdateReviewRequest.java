package com.example.aichinese.dto.communication;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
        @NotNull(message = "Rating is required.")
        @DecimalMin(value = "1.0", message = "Rating must be at least 1.")
        @DecimalMax(value = "5.0", message = "Rating must be at most 5.")
        BigDecimal rating,

        @Size(max = 2000, message = "Review comment must be 2000 characters or fewer.")
        String comment) {
}
