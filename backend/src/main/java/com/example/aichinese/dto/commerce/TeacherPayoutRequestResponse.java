package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PayoutStatus;

@JsonPropertyOrder({
        "id",
        "amount",
        "currencyCode",
        "requestedNote",
        "decisionReason",
        "payoutStatus",
        "createdAt"
})
public record TeacherPayoutRequestResponse(
        Long id,
        BigDecimal amount,
        String currencyCode,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
