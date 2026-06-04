package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherEarningStatus;

@JsonPropertyOrder({
        "id",
        "orderItemId",
        "itemTitle",
        "grossAmount",
        "commissionAmount",
        "netAmount",
        "currencyCode",
        "earningStatus",
        "createdAt"
})
public record TeacherEarningResponse(
        Long id,
        Long orderItemId,
        String itemTitle,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String currencyCode,
        TeacherEarningStatus earningStatus,
        LocalDateTime createdAt) {
}
