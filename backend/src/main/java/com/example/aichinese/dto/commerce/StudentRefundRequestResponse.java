package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.RefundStatus;

@JsonPropertyOrder({
        "id",
        "orderId",
        "orderNumber",
        "amount",
        "reason",
        "decisionReason",
        "refundStatus",
        "createdAt"
})
public record StudentRefundRequestResponse(
        Long id,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        String reason,
        String decisionReason,
        RefundStatus refundStatus,
        LocalDateTime createdAt) {
}
