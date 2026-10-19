package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "packageId", "packageTitle", "learningType" })
public record LiveClassPackageSummaryResponse(
        Long packageId,
        String packageTitle,
        String learningType) {

    public LiveClassPackageSummaryResponse {
        Objects.requireNonNull(packageId, "packageId must not be null");
        Objects.requireNonNull(packageTitle, "packageTitle must not be null");
    }
}
