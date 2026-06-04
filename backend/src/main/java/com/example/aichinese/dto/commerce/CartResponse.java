package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "cartId", "items", "totalAmount", "currencyCode", "empty" })
public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currencyCode,
        boolean empty) {

    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
