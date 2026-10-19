package com.example.aichinese.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

@Component
public class OpenAiCompatibleAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProviderClient.class);

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    static final String IMAGE_INPUT_UNSUPPORTED = "Image input is not supported by the configured provider.";

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleAiProviderClient(
            AiProperties aiProperties,
            RestTemplateBuilder restTemplateBuilder) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        this.restTemplate = AiProviderSupport.restTemplate(aiProperties, restTemplateBuilder);
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");

        String providerName = AiProviderSupport.normalizeProvider(aiProperties.getProvider());
        String modelName = TextUtils.trimToNull(aiProperties.getModelName());
        if (prompt.hasImage()) {
            // Chat Completions-compatible providers are configured for text only in this prototype.
            return AiProviderResult.failure(providerName, modelName, IMAGE_INPUT_UNSUPPORTED);
        }
        if (modelName == null) {
            return AiProviderResult.failure(providerName, null, "AI model name is missing.");
        }

        String apiKey = TextUtils.trimToNull(aiProperties.getApiKey());
        if (apiKey == null) {
            return AiProviderResult.failure(providerName, modelName, "AI API key is missing.");
        }

        String endpoint = AiProviderSupport.endpoint(aiProperties, CHAT_COMPLETIONS_PATH);
        if (endpoint == null) {
            return AiProviderResult.failure(providerName, modelName, "AI API base URL is missing or invalid.");
        }

        return executeRequest(
                providerName,
                modelName,
                endpoint,
                AiProviderSupport.bearerJsonHeaders(apiKey),
                buildRequestBody(modelName, prompt),
                prompt.featureKey());
    }

    private static Map<String, Object> buildRequestBody(String modelName, AiProviderPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", buildMessages(prompt));
        body.put("max_tokens", prompt.maxOutputTokens());
        body.put("temperature", DEFAULT_TEMPERATURE);
        if (prompt.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private static List<Map<String, Object>> buildMessages(AiProviderPrompt prompt) {
        List<Map<String, Object>> messages = new ArrayList<>(prompt.messageHistory().size() + 2);
        messages.add(message(ROLE_SYSTEM, prompt.systemInstruction()));
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            messages.add(message(message.role(), message.content()));
        }
        messages.add(message(ROLE_USER, prompt.userPrompt()));
        return messages;
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
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
                    AiProviderSupport.MAP_RESPONSE_TYPE);

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

        Integer tokenCount = AiProviderSupport.extractTokenCount(responseBody.get("usage"));
        return AiProviderResult.success(providerName, modelName, text.trim(), tokenCount);
    }
}
