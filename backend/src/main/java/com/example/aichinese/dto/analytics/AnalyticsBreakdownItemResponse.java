package com.example.aichinese.dto.analytics;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "label", "value" })
public record AnalyticsBreakdownItemResponse(
        String label,
        long value) {

    public AnalyticsBreakdownItemResponse {
        Objects.requireNonNull(label, "label must not be null");
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "value must be non-negative but was " + value);
        }
    }
}
