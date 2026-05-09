package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

public record SubscriptionPlanResponse(
        Long id,
        String name,
        BigDecimal price,
        String currencyCode,
        String benefits,
        boolean active) {
}
