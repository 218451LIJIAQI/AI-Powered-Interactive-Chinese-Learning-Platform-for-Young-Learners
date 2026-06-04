package com.example.aichinese.service.ai;

import java.math.BigDecimal;
import java.math.BigInteger;
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
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

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
        } catch (RuntimeException ex) {
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
        if (totalTokens instanceof Integer integer) {
            return integer >= 0 ? integer : null;
        }
        if (totalTokens instanceof Long longValue) {
            return longValue >= 0 && longValue <= Integer.MAX_VALUE
                    ? Math.toIntExact(longValue)
                    : null;
        }
        if (totalTokens instanceof BigInteger bigInteger) {
            return bigInteger.signum() >= 0 && bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0
                    ? bigInteger.intValue()
                    : null;
        }
        if (totalTokens instanceof BigDecimal bigDecimal) {
            try {
                BigInteger exact = bigDecimal.toBigIntegerExact();
                return exact.signum() >= 0 && exact.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0
                        ? exact.intValue()
                        : null;
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        if (totalTokens instanceof Number number) {
            long tokenCount = number.longValue();
            return tokenCount >= 0 && tokenCount <= Integer.MAX_VALUE ? (int) tokenCount : null;
        }
        return null;
    }
}
