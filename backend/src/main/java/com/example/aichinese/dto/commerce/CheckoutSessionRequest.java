package com.example.aichinese.dto.commerce;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CheckoutSessionRequest(
        @NotEmpty(message = "At least one checkout item is required.")
        List<@Valid CheckoutSessionItemRequest> items,
        @NotNull(message = "Checkout source is required.")
        CheckoutSource source) {

    public enum CheckoutSource {
        CART,
        BUY_NOW
    }
}
