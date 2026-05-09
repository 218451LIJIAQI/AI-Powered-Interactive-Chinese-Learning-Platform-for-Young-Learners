package com.example.aichinese.service.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .setConnectTimeout(Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1)))
                .setReadTimeout(Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1)))
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        String endpoint = normalizeBaseUrl(aiProperties.getApiBaseUrl()) + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey().trim());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", prompt.systemInstruction()));
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        messages.add(Map.of("role", "user", "content", prompt.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.getModelName());
        body.put("messages", messages);
        body.put("max_tokens", prompt.maxOutputTokens());
        body.put("temperature", 0.7);
        if (prompt.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider returned an empty response body.");
            }

            Object choicesObject = responseBody.get("choices");
            if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
                return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider response did not contain any choices.");
            }

            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
                return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider response choice format was invalid.");
            }

            Object messageObject = choiceMap.get("message");
            if (!(messageObject instanceof Map<?, ?> messageMap)) {
                return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider response message format was invalid.");
            }

            Object content = messageMap.get("content");
            if (!(content instanceof String text) || text.isBlank()) {
                return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider response text was empty.");
            }

            Integer tokenCount = extractTokenCount(responseBody.get("usage"));
            return AiProviderResult.success(providerName(), aiProperties.getModelName(), text.trim(), tokenCount);
        } catch (RestClientException exception) {
            log.warn("AI provider request failed for feature {}: {}", prompt.featureKey(), exception.getMessage());
            return AiProviderResult.failure(providerName(), aiProperties.getModelName(), "Provider request failed or timed out.");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "https://api.deepseek.com";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
}
