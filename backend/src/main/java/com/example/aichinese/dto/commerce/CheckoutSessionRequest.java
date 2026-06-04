package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder({ "items", "source" })
public record CheckoutSessionRequest(
        @NotEmpty(message = "At least one checkout item is required.")
        List<@NotNull(message = "Checkout item must not be null.") @Valid CheckoutSessionItemRequest> items,
        @NotNull(message = "Checkout source is required.")
        CheckoutSource source) {

    public CheckoutSessionRequest {
        items = immutableListOrEmpty(items);
    }

    public enum CheckoutSource {
        CART,
        BUY_NOW
    }
}
