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

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /**
     * Text generation is available; image analysis (handwriting feedback) is available only when the
     * configured provider accepts image input. Audio analysis is never available with the configured models.
     */
    public static AiAvailabilityResponse available(
            String provider,
            String model,
            String message,
            boolean imageAnalysisAvailable) {
        return new AiAvailabilityResponse(
                true,
                provider,
                model,
                STATUS_AVAILABLE,
                message,
                true,
                false,
                imageAnalysisAvailable);
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
