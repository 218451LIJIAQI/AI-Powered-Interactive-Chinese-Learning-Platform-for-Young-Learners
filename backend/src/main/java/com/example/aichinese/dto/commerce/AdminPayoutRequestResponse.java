package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.PayoutStatus;

@JsonPropertyOrder({
        "id",
        "teacherId",
        "teacherName",
        "teacherEmail",
        "amount",
        "currencyCode",
        "requestedNote",
        "decisionReason",
        "payoutStatus",
        "createdAt"
})
public record AdminPayoutRequestResponse(
        Long id,
        Long teacherId,
        String teacherName,
        String teacherEmail,
        BigDecimal amount,
        String currencyCode,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
