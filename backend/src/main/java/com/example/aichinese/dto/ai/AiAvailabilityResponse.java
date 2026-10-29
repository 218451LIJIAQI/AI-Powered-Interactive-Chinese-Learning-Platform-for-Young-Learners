package com.example.aichinese.dto.ai;

/**
 * Whether the AI provider can answer. Handwriting checks need a provider that accepts images, pronunciation checks one
 * with a speech-to-text model; both are false while the provider is unavailable.
 */
public record AiAvailabilityResponse(
        boolean available,
        String provider,
        String model,
        String status,
        String message,
        boolean imageAnalysisAvailable,
        boolean speechAnalysisAvailable) {

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public static AiAvailabilityResponse available(
            String provider,
            String model,
            String message,
            boolean imageAnalysisAvailable,
            boolean speechAnalysisAvailable) {
        return new AiAvailabilityResponse(
                true, provider, model, STATUS_AVAILABLE, message, imageAnalysisAvailable, speechAnalysisAvailable);
    }

    public static AiAvailabilityResponse unavailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(false, provider, model, STATUS_UNAVAILABLE, message, false, false);
    }
}
