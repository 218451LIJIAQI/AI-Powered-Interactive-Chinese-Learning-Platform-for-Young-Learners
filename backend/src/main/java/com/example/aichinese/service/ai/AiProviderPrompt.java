package com.example.aichinese.service.ai;

import java.util.Base64;
import java.util.List;

/**
 * Provider-neutral request passed to an {@link AiProviderClient}.
 *
 * @param image optional learner image (for example a handwriting photo); {@code null} for
 *              text-only requests. Only the OpenAI Responses client sends it; AiAvailabilityService
 *              offers image analysis for that provider only.
 */
public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory,
        AiImageInput image) {

    public boolean hasImage() {
        return image != null;
    }

    public record AiMessage(String role, String content) {
    }

    /**
     * Image bytes plus MIME type ({@code image/png} or {@code image/jpeg}).
     */
    public record AiImageInput(byte[] data, String mimeType) {

        /** {@code data:<mime>;base64,<payload>} as accepted by the OpenAI Responses API. */
        public String toDataUrl() {
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        }
    }
}
