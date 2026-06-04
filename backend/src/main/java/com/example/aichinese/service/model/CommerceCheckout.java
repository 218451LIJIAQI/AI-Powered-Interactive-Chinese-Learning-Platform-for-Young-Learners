package com.example.aichinese.service.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;

public record CommerceCheckout(
        StudentProfile student,
        Order order,
        PaymentRecord paymentRecord,
        List<OrderItem> orderItems,
        BigDecimal totalAmount,
        String currencyCode,
        String successUrl,
        String cancelUrl) {

    public CommerceCheckout {
        orderItems = List.copyOf(Objects.requireNonNull(orderItems, "orderItems"));
        totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
        currencyCode = requireText(currencyCode, "currencyCode");
        successUrl = requireText(successUrl, "successUrl");
        cancelUrl = requireText(cancelUrl, "cancelUrl");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }
}
