package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.PayoutStatus;

public record AdminPayoutRequestResponse(
        Long id,
        Long teacherId,
        String teacherName,
        String teacherEmail,
        BigDecimal amount,
        String requestedNote,
        String decisionReason,
        PayoutStatus payoutStatus,
        LocalDateTime createdAt) {
}
