package com.example.aichinese.service.ai;

public record AiProviderResult(boolean success, String text, Integer tokenCount, String errorSummary) {

    public AiProviderResult {
        // A reply of control characters only (for example U+0000) passes the clients' isBlank() test but trims to "";
        // HttpAiProviderClient turns this exception into the controlled failure "Provider request failed or timed out.".
        if (success && text.isBlank()) {
            throw new IllegalArgumentException("Successful provider result text must not be blank.");
        }
    }

    public static AiProviderResult success(String text, Integer tokenCount) {
        return new AiProviderResult(true, text, tokenCount, null);
    }

    public static AiProviderResult failure(String errorSummary) {
        return new AiProviderResult(false, null, null, errorSummary);
    }
}
