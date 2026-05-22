package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

public record TeacherEarningSummaryResponse(
        BigDecimal grossAmount,
        BigDecimal totalNetAmount,
        BigDecimal availableBalance,
        BigDecimal pendingBalance) {
}
