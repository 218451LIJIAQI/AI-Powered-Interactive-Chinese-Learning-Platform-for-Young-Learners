package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;

public record AnalyticsTrendPointResponse(
        String label,
        BigDecimal value) {
}
