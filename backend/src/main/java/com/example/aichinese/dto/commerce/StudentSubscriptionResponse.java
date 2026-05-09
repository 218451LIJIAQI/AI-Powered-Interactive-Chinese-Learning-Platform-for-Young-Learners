package com.example.aichinese.dto.commerce;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.PaymentStatus;

public record StudentSubscriptionResponse(
        Long id,
        SubscriptionPlanResponse plan,
        PaymentStatus subscriptionStatus,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Long paymentId) {
}
