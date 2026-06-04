package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "available",
        "provider",
        "model",
        "status",
        "message",
        "textGenerationAvailable",
        "audioAnalysisAvailable",
        "imageAnalysisAvailable"
})
public record AiAvailabilityResponse(
        boolean available,
        String provider,
        String model,
        String status,
        String message,
        boolean textGenerationAvailable,
        boolean audioAnalysisAvailable,
        boolean imageAnalysisAvailable) {

    public static final String STATUS_AVAILABLE = "AVAILABLE";

    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public static AiAvailabilityResponse textGenerationAvailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(
                true,
                provider,
                model,
                STATUS_AVAILABLE,
                message,
                true,
                false,
                false);
    }

    public static AiAvailabilityResponse unavailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(
                false,
                provider,
                model,
                STATUS_UNAVAILABLE,
                message,
                false,
                false,
                false);
    }
}
