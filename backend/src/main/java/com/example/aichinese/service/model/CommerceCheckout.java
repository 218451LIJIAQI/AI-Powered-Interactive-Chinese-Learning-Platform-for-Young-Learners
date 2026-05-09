package com.example.aichinese.service.model;

import java.math.BigDecimal;
import java.util.List;

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
}
