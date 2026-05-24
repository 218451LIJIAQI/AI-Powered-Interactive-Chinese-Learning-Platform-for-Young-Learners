package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code app.ai.*} configuration block.
 *
 * <p>Carries the AI provider settings consumed by both the runtime AI
 * service and the {@link AiProductionGuard} startup validator. Setters
 * for {@link #timeoutSeconds} and {@link #maxOutputTokens} validate
 * their range eagerly so a typo in {@code application.yml} fails at
 * binding time rather than at first request.</p>
 *
 * <p>The class is intentionally a mutable JavaBean (rather than a
 * {@code record}) because Spring Boot relaxed-binding requires setter
 * methods to support both kebab-case and SCREAMING_SNAKE_CASE
 * environment-variable overrides for the same property name.</p>
 */
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
        this.provider = provider;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("AI timeout must be between 1 and 60 seconds.");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        if (maxOutputTokens < MIN_OUTPUT_TOKENS || maxOutputTokens > MAX_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("AI max output tokens must be between 1 and 4000.");
        }
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isEnableRealProvider() {
        return enableRealProvider;
    }

    public void setEnableRealProvider(boolean enableRealProvider) {
        this.enableRealProvider = enableRealProvider;
    }
}
