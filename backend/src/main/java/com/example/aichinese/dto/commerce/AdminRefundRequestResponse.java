package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.RefundStatus;

@JsonPropertyOrder({
        "id",
        "orderId",
        "orderNumber",
        "studentId",
        "studentName",
        "studentEmail",
        "amount",
        "reason",
        "decisionReason",
        "refundStatus",
        "createdAt"
})
public record AdminRefundRequestResponse(
        Long id,
        Long orderId,
        String orderNumber,
        Long studentId,
        String studentName,
        String studentEmail,
        BigDecimal amount,
        String reason,
        String decisionReason,
        RefundStatus refundStatus,
        LocalDateTime createdAt) {
}
