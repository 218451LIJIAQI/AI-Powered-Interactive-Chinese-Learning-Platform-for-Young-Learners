package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "cartItemId",
        "itemType",
        "itemId",
        "title",
        "unitPrice",
        "currencyCode",
        "quantity",
        "subtotal",
        "teacherOwned"
})
public record CartItemResponse(
        Long cartItemId,
        String itemType,
        Long itemId,
        String title,
        BigDecimal unitPrice,
        String currencyCode,
        Integer quantity,
        BigDecimal subtotal,
        boolean teacherOwned) {
}
