package com.example.aichinese.service.ai;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;

@Component
public class OpenAiCompatibleAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProviderClient.class);

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        this.aiProperties = aiProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1)))
                .readTimeout(Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1)))
                .build();
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        String providerName = providerName();
        String modelName = trimToNull(aiProperties.getModelName());
        if (modelName == null) {
            return AiProviderResult.failure(providerName, null, "AI model name is missing.");
        }

        String apiKey = trimToNull(aiProperties.getApiKey());
        if (apiKey == null) {
            return AiProviderResult.failure(providerName, modelName, "AI API key is missing.");
        }

        String endpoint = resolveEndpoint(aiProperties.getApiBaseUrl());
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
        body.put("temperature", 0.7);
        if (prompt.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return AiProviderResult.failure(providerName, modelName, "Provider returned an empty response body.");
            }

            Object choicesObject = responseBody.get("choices");
            if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
                return AiProviderResult.failure(providerName, modelName, "Provider response did not contain any choices.");
            }

            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
                return AiProviderResult.failure(providerName, modelName, "Provider response choice format was invalid.");
            }

            Object messageObject = choiceMap.get("message");
            if (!(messageObject instanceof Map<?, ?> messageMap)) {
                return AiProviderResult.failure(providerName, modelName, "Provider response message format was invalid.");
            }

            Object content = messageMap.get("content");
            if (!(content instanceof String text) || text.isBlank()) {
                return AiProviderResult.failure(providerName, modelName, "Provider response text was empty.");
            }

            Integer tokenCount = extractTokenCount(responseBody.get("usage"));
            return AiProviderResult.success(providerName, modelName, text.trim(), tokenCount);
        } catch (RestClientException exception) {
            log.warn("AI provider request failed for feature {}: {}", prompt.featureKey(), exception.getMessage());
            return AiProviderResult.failure(providerName, modelName, "Provider request failed or timed out.");
        }
    }

    private String resolveEndpoint(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            normalized = "https://api.deepseek.com";
        }
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return null;
            }
            if ("http".equals(scheme) && !isLoopbackHost(uri.getHost())) {
                return null;
            }
            return normalized + "/chat/completions";
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private Integer extractTokenCount(Object usageObject) {
        if (!(usageObject instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String providerName() {
        return aiProperties.getProvider() == null || aiProperties.getProvider().isBlank()
                ? "deepseek"
                : aiProperties.getProvider().trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
