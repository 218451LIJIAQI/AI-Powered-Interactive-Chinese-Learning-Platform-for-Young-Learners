package com.example.aichinese.dto.commerce;

import com.example.aichinese.entity.enums.PaymentStatus;

public record StripeWebhookResultResponse(
        String eventId,
        String eventType,
        Long paymentId,
        PaymentStatus paymentStatus,
        String providerStatus,
        boolean processed) {
}
