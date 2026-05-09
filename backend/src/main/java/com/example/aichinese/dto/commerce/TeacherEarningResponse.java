package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TeacherEarningResponse(
        Long id,
        Long orderItemId,
        String itemTitle,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String earningStatus,
        LocalDateTime createdAt) {
}
