package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

@JsonPropertyOrder({
        "orderId",
        "orderNumber",
        "paymentId",
        "paymentStatus",
        "totalAmount",
        "currencyCode",
        "checkoutUrl",
        "providerName",
        "providerStatus",
        "paymentAvailable",
        "message",
        "items"
})
public record CheckoutResponse(
        Long orderId,
        String orderNumber,
        Long paymentId,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        String currencyCode,
        String checkoutUrl,
        String providerName,
        String providerStatus,
        boolean paymentAvailable,
        String message,
        List<CartItemResponse> items) {

    public CheckoutResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
