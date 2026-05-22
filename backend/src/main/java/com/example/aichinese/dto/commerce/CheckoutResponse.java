package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

import com.example.aichinese.entity.enums.PaymentStatus;

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
}
