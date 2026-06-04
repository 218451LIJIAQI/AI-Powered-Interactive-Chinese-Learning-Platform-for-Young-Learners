package com.example.aichinese.dto.analytics;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "label", "value" })
public record AnalyticsTrendPointResponse(
        String label,
        BigDecimal value) {

    public AnalyticsTrendPointResponse {
        Objects.requireNonNull(label, "label must not be null");
        if (value == null) {
            value = BigDecimal.ZERO;
        }
    }
}
