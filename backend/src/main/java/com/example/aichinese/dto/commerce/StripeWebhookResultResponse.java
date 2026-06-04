package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

@JsonPropertyOrder({
        "eventId",
        "eventType",
        "paymentId",
        "paymentStatus",
        "providerStatus",
        "processed"
})
public record StripeWebhookResultResponse(
        String eventId,
        String eventType,
        Long paymentId,
        PaymentStatus paymentStatus,
        String providerStatus,
        boolean processed) {
}
