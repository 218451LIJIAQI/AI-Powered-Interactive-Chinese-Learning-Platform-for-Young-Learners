package com.example.aichinese.service.ai;

public record AiProviderResult(
        boolean success,
        String providerName,
        String modelName,
        String text,
        Integer tokenCount,
        String errorSummary) {

    public AiProviderResult {
        if (success) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Successful provider result must carry non-blank text.");
            }
            if (tokenCount != null && tokenCount < 0) {
                throw new IllegalArgumentException(
                        "Successful provider result token count must be non-negative.");
            }
            if (errorSummary != null) {
                throw new IllegalArgumentException(
                        "Successful provider result must not carry an error summary.");
            }
        } else {
            if (errorSummary == null || errorSummary.isBlank()) {
                throw new IllegalArgumentException(
                        "Failed provider result must carry a non-blank error summary.");
            }
            if (text != null) {
                throw new IllegalArgumentException(
                        "Failed provider result must not carry response text.");
            }
            if (tokenCount != null) {
                throw new IllegalArgumentException(
                        "Failed provider result must not carry a token count.");
            }
        }
    }

    public static AiProviderResult success(
            String providerName,
            String modelName,
            String text,
            Integer tokenCount) {
        return new AiProviderResult(true, providerName, modelName, text, tokenCount, null);
    }

    public static AiProviderResult failure(
            String providerName,
            String modelName,
            String errorSummary) {
        return new AiProviderResult(false, providerName, modelName, null, null, errorSummary);
    }
}
