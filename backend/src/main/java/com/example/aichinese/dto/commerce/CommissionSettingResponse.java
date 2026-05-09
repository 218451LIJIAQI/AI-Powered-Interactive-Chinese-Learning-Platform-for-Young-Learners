package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

public record CommissionSettingResponse(
        BigDecimal commissionRate,
        String settingKey,
        String description) {
}
