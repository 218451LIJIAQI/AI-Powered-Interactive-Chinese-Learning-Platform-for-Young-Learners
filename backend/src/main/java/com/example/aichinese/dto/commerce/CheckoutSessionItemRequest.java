package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckoutSessionItemRequest(
        @NotBlank(message = "Item type is required.")
        String itemType,
        @NotNull(message = "Item id is required.")
        Long itemId,
        String featureKey) {
}
