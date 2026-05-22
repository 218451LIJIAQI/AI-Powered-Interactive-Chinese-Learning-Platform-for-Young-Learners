package com.example.aichinese.dto.ai;

public record AiAvailabilityResponse(
        boolean available,
        String provider,
        String model,
        String status,
        String message,
        boolean textGenerationAvailable,
        boolean audioAnalysisAvailable,
        boolean imageAnalysisAvailable) {
}
