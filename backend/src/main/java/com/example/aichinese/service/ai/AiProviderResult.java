package com.example.aichinese.service.ai;

public record AiProviderResult(
        boolean success,
        String providerName,
        String modelName,
        String text,
        Integer tokenCount,
        String errorSummary) {

    public static AiProviderResult success(String providerName, String modelName, String text, Integer tokenCount) {
        return new AiProviderResult(true, providerName, modelName, text, tokenCount, null);
    }

    public static AiProviderResult failure(String providerName, String modelName, String errorSummary) {
        return new AiProviderResult(false, providerName, modelName, null, null, errorSummary);
    }
}
