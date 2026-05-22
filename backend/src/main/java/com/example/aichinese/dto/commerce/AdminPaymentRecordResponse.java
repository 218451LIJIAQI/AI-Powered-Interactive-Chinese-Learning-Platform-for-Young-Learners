package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.PaymentStatus;

public record AdminPaymentRecordResponse(
        Long paymentId,
        Long orderId,
        String orderNumber,
        Long studentId,
        String studentName,
        String studentEmail,
        String providerName,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus paymentStatus,
        String paymentMethod,
        String providerStatus,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        List<CartItemResponse> items) {
}
