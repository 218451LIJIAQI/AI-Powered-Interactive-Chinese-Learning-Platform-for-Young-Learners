package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "packageId", "packageTitle", "learningType", "accessType" })
public record LiveClassPackageSummaryResponse(
        Long packageId,
        String packageTitle,
        String learningType,
        String accessType) {

    public LiveClassPackageSummaryResponse {
        Objects.requireNonNull(packageId, "packageId must not be null");
        Objects.requireNonNull(packageTitle, "packageTitle must not be null");
    }
}
