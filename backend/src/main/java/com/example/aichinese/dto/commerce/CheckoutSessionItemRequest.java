package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CheckoutSessionItemRequest(
        @NotBlank(message = "Item type is required.")
        @Size(max = 30, message = "Item type must be 30 characters or fewer.")
        String itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @Size(max = 100, message = "Feature key must be 100 characters or fewer.")
        String featureKey) {
}
