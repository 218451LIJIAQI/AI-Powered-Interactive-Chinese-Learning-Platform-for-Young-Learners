package com.example.aichinese.dto.learning;

public record LiveClassPackageSummaryResponse(
        Long packageId,
        String packageTitle,
        String learningType,
        String accessType) {
}
