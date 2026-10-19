package com.example.aichinese.service.ai;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Provider-neutral request passed to an {@link AiProviderClient}.
 *
 * @param image optional learner image (for example a handwriting photo); {@code null} for
 *              text-only requests. Only providers that support image input use it; the
 *              others must report it as unsupported.
 */
public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        String languagePreference,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory,
        AiImageInput image) {

    public AiProviderPrompt {
        featureKey = requireNonBlank(featureKey, "featureKey");
        systemInstruction = requireNonBlank(systemInstruction, "systemInstruction");
        userPrompt = requireNonBlank(userPrompt, "userPrompt");
        languagePreference = requireNonBlank(languagePreference, "languagePreference");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0, got " + maxOutputTokens);
        }
        Objects.requireNonNull(messageHistory, "messageHistory");
        messageHistory = List.copyOf(messageHistory);
    }

    /** Text-only prompt. */
    public AiProviderPrompt(
            String featureKey,
            String systemInstruction,
            String userPrompt,
            String languagePreference,
            int maxOutputTokens,
            boolean expectJson,
            List<AiMessage> messageHistory) {
        this(featureKey, systemInstruction, userPrompt, languagePreference, maxOutputTokens, expectJson,
                messageHistory, null);
    }

    public boolean hasImage() {
        return image != null;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }

    public record AiMessage(String role, String content) {

        public AiMessage {
            role = requireNonBlank(role, "role");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * Image bytes plus MIME type ({@code image/png} or {@code image/jpeg}).
     */
    public record AiImageInput(byte[] data, String mimeType) {

        public AiImageInput {
            Objects.requireNonNull(data, "data");
            if (data.length == 0) {
                throw new IllegalArgumentException("Image data must not be empty.");
            }
            mimeType = requireNonBlank(mimeType, "mimeType").strip().toLowerCase(Locale.ROOT);
            if (!mimeType.startsWith("image/")) {
                throw new IllegalArgumentException("mimeType must be an image type.");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        /** {@code data:<mime>;base64,<payload>} as accepted by the OpenAI Responses API. */
        public String toDataUrl() {
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AiImageInput that
                    && mimeType.equals(that.mimeType)
                    && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return 31 * mimeType.hashCode() + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "AiImageInput[mimeType=" + mimeType + ", bytes=" + data.length + "]";
        }
    }
}
