package com.example.aichinese.config;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.example.aichinese.util.TextUtils;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** OpenAI Responses API (GPT-5.6 Luna), the provider named in the project proposal. */
    public static final String OPENAI = "openai";

    /** Alternative Chat Completions-compatible providers kept behind the same configurable service. */
    public static final String DEEPSEEK = "deepseek";

    public static final String OPENAI_COMPATIBLE = "openai-compatible";

    public static final Set<String> SUPPORTED_PROVIDERS = Set.of(OPENAI, DEEPSEEK, OPENAI_COMPATIBLE);

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 60;
    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 4000;

    private String provider = "openai";
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String modelName = "gpt-5.6-luna";
    /** Speech-to-text model that writes down a pronunciation recording before it is scored (OpenAI only). */
    private String transcriptionModel = "gpt-4o-mini-transcribe";
    private int timeoutSeconds = 30;
    private int maxOutputTokens = 1200;
    private boolean enableRealProvider;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = trim(provider);
    }

    /** The provider in lower case: {@link #OPENAI} when unset, and {@code openai_compatible} read as {@link #OPENAI_COMPATIBLE}. */
    public String normalizedProvider() {
        String stripped = TextUtils.trimToNull(provider);
        if (stripped == null) {
            return OPENAI;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(lower) ? OPENAI_COMPATIBLE : lower;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = trim(apiBaseUrl);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = trim(apiKey);
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = trim(modelName);
    }

    public String getTranscriptionModel() {
        return transcriptionModel;
    }

    public void setTranscriptionModel(String transcriptionModel) {
        this.transcriptionModel = trim(transcriptionModel);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = requireRange(
                timeoutSeconds, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, "AI timeout", " seconds");
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = requireRange(
                maxOutputTokens, MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS, "AI max output tokens", "");
    }

    public boolean isEnableRealProvider() {
        return enableRealProvider;
    }

    public void setEnableRealProvider(boolean enableRealProvider) {
        this.enableRealProvider = enableRealProvider;
    }

    private static int requireRange(int value, int min, int max, String label, String suffix) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + suffix + ".");
        }
        return value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String apiKeyStatus() {
        return apiKey == null || apiKey.isBlank() ? "[unset]" : "[set]";
    }

    @Override
    public String toString() {
        return "AiProperties[provider=" + provider
                + ", apiBaseUrl=" + apiBaseUrl
                + ", apiKey=" + apiKeyStatus()
                + ", modelName=" + modelName
                + ", transcriptionModel=" + transcriptionModel
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxOutputTokens=" + maxOutputTokens
                + ", enableRealProvider=" + enableRealProvider
                + "]";
    }
}
