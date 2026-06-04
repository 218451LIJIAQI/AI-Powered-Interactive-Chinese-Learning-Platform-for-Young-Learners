package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "commissionRate", "settingKey", "description" })
public record CommissionSettingResponse(
        BigDecimal commissionRate,
        String settingKey,
        String description) {
}
