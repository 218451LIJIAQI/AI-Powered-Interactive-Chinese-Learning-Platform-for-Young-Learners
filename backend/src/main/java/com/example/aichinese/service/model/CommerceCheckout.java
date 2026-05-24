package com.example.aichinese.service.model;

import java.math.BigDecimal;
import java.util.List;

import com.example.aichinese.entity.Order;
import com.example.aichinese.entity.OrderItem;
import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.StudentProfile;

/**
 * Intermediate carrier returned by the checkout / order-creation
 * pipeline so the controller and payment provider receive a single,
 * fully-resolved snapshot of what the student is about to pay for.
 *
 * <p>Built by {@link com.example.aichinese.service.CheckoutService}
 * after cart validation, persisted-order creation, and
 * payment-record minting, then handed to
 * {@link com.example.aichinese.service.PaymentService} to produce the
 * provider redirect URL. The {@code successUrl} / {@code cancelUrl}
 * are computed from {@link com.example.aichinese.config.PaymentProperties}
 * so the provider always returns the learner to a well-known
 * front-end route under the platform's own origin.</p>
 */
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
