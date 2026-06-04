package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "badgeId", "name", "description", "iconKey", "awardedAt" })
public record BadgeSummaryResponse(
        Long badgeId,
        String name,
        String description,
        String iconKey,
        LocalDateTime awardedAt) {

    public BadgeSummaryResponse {
        Objects.requireNonNull(badgeId, "badgeId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(awardedAt, "awardedAt must not be null");
    }
}
