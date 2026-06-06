package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 60;
    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 4000;

    private String provider = "deepseek";
    private String apiBaseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String modelName = "deepseek-chat";
    private int timeoutSeconds = 30;
    private int maxOutputTokens = 1200;
    private boolean enableRealProvider;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = trim(provider);
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
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxOutputTokens=" + maxOutputTokens
                + ", enableRealProvider=" + enableRealProvider
                + "]";
    }
}
