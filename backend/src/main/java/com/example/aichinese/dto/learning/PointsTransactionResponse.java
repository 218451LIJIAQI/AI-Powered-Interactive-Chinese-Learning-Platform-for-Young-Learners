package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "pointsDelta",
        "reason",
        "sourceType",
        "sourceId",
        "createdAt"
})
public record PointsTransactionResponse(
        Long id,
        Integer pointsDelta,
        String reason,
        String sourceType,
        Long sourceId,
        LocalDateTime createdAt) {

    public PointsTransactionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pointsDelta, "pointsDelta must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
