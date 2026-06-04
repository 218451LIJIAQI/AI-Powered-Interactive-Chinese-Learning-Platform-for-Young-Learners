package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

@JsonPropertyOrder({
        "paymentId",
        "orderId",
        "orderNumber",
        "providerName",
        "amount",
        "currencyCode",
        "paymentStatus",
        "paymentMethod",
        "providerStatus",
        "paidAt",
        "createdAt",
        "items"
})
public record StudentPaymentRecordResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        String providerName,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus paymentStatus,
        String paymentMethod,
        String providerStatus,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        List<CartItemResponse> items) {

    public StudentPaymentRecordResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
