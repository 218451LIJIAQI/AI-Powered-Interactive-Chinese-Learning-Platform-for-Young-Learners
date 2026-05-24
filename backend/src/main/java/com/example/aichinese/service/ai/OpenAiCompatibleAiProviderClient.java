package com.example.aichinese.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

/**
 * {@link AiProviderClient} that talks to any OpenAI-compatible
 * {@code /chat/completions} endpoint (DeepSeek, OpenAI, Together,
 * etc.).
 *
 * <p>Picked at runtime by {@link AiProviderRouter} when
 * {@code app.ai.enable-real-provider=true} and the configured
 * provider is one of {@code deepseek} / {@code openai-compatible}.
 * Configuration that is missing or invalid (no model, no API key,
 * non-HTTPS base URL, etc.) is detected before any network call so
 * the caller gets an immediate {@link AiProviderResult#failure}
 * rather than a delayed timeout.</p>
 *
 * <h2>Endpoint integrity</h2>
 * <p>URL validation is delegated to
 * {@link AiProviderSupport#validateAndNormalizeBaseUrl(String)} which
 * accepts only HTTPS URLs in production; HTTP is allowed only when
 * the host is loopback (integration tests / local LLM servers).
 * This is defence-in-depth on top of
 * {@link com.example.aichinese.config.AiProductionGuard}.</p>
 *
 * <h2>Failure handling</h2>
 * <p>Every {@link RestClientException} is caught and translated into a
 * generic failure result; the underlying message is logged at
 * {@code WARN} but never propagated to the response — so a provider
 * 5xx body cannot leak prompt fragments back to the front-end.</p>
 */
@Component
public class OpenAiCompatibleAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProviderClient.class);

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final double DEFAULT_TEMPERATURE = 0.7;

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleAiProviderClient(
            AiProperties aiProperties,
            RestTemplateBuilder restTemplateBuilder) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        Objects.requireNonNull(restTemplateBuilder, "restTemplateBuilder");
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1));
        this.restTemplate = restTemplateBuilder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");

        String providerName = AiProviderSupport.normalizeProvider(aiProperties.getProvider());
        String modelName = TextUtils.trimToNull(aiProperties.getModelName());
        if (modelName == null) {
            return AiProviderResult.failure(providerName, null, "AI model name is missing.");
        }

        String apiKey = TextUtils.trimToNull(aiProperties.getApiKey());
        if (apiKey == null) {
            return AiProviderResult.failure(providerName, modelName, "AI API key is missing.");
        }

        String endpoint = resolveEndpoint();
        if (endpoint == null) {
            return AiProviderResult.failure(providerName, modelName, "AI API base URL is missing or invalid.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", prompt.systemInstruction()));
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        messages.add(Map.of("role", "user", "content", prompt.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("max_tokens", prompt.maxOutputTokens());
        body.put("temperature", DEFAULT_TEMPERATURE);
        if (prompt.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        return executeRequest(providerName, modelName, endpoint, headers, body, prompt.featureKey());
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private AiProviderResult executeRequest(
            String providerName,
            String modelName,
            String endpoint,
            HttpHeaders headers,
            Map<String, Object> body,
            String featureKey) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return parseResponse(providerName, modelName, response.getBody());
        } catch (RestClientException ex) {
            log.warn("AI provider request failed for feature {} with exception type {}.",
                    featureKey, ex.getClass().getSimpleName());
            return AiProviderResult.failure(providerName, modelName,
                    "Provider request failed or timed out.");
        }
    }

    private AiProviderResult parseResponse(
            String providerName,
            String modelName,
            Map<String, Object> responseBody) {
        if (responseBody == null) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider returned an empty response body.");
        }

        Object choicesObject = responseBody.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response did not contain any choices.");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response choice format was invalid.");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response message format was invalid.");
        }

        Object content = messageMap.get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response text was empty.");
        }

        Integer tokenCount = extractTokenCount(responseBody.get("usage"));
        return AiProviderResult.success(providerName, modelName, text.trim(), tokenCount);
    }

    private String resolveEndpoint() {
        String raw = TextUtils.trimToNull(aiProperties.getApiBaseUrl());
        String baseUrl = AiProviderSupport.validateAndNormalizeBaseUrl(
                raw != null ? raw : DEFAULT_BASE_URL);
        return baseUrl != null ? baseUrl + CHAT_COMPLETIONS_PATH : null;
    }

    private static Integer extractTokenCount(Object usageObject) {
        if (!(usageObject instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
