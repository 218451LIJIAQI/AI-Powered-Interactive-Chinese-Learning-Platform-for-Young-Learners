package com.example.aichinese.service.ai;

/** A provider's answer: non-blank {@code text} on success, otherwise a safe {@code errorSummary} for the usage log. */
public record AiProviderResult(boolean success, String text, Integer tokenCount, String errorSummary) {

    public static AiProviderResult success(String text, Integer tokenCount) {
        return new AiProviderResult(true, text, tokenCount, null);
    }

    public static AiProviderResult failure(String errorSummary) {
        return new AiProviderResult(false, null, null, errorSummary);
    }

    public static AiProviderResult failure(String errorSummary, Integer tokenCount) {
        return new AiProviderResult(false, null, tokenCount, errorSummary);
    }
}
