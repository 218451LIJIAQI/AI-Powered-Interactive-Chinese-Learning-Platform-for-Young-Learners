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

/**
 * Default AI provider client for the prototype. It calls the OpenAI Responses API
 * ({@code POST /v1/responses}) with the configured model (GPT-5.6 Luna by default),
 * as specified in the project proposal. Bounded-AI instructions are sent through the
 * {@code instructions} field, responses are not stored by the provider ({@code store=false}),
 * and any provider error is converted into a controlled failure result.
 */
@Component
public class OpenAiResponsesAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesAiProviderClient.class);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String RESPONSES_PATH = "/responses";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_INCOMPLETE = "incomplete";
    /** Low detail keeps image token usage bounded; a single handwritten character does not need more. */
    private static final String IMAGE_DETAIL = "low";
    private static final BigInteger INTEGER_MAX_VALUE = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public OpenAiResponsesAiProviderClient(
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

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(buildRequestBody(modelName, prompt), buildHeaders(apiKey)),
                    MAP_RESPONSE_TYPE);
            return parseResponse(providerName, modelName, response.getBody(), prompt.expectJson());
        } catch (RuntimeException ex) {
            log.warn("AI provider request failed for feature {} with exception type {}.",
                    prompt.featureKey(), ex.getClass().getSimpleName());
            return AiProviderResult.failure(providerName, modelName,
                    "Provider request failed or timed out.");
        }
    }

    private static HttpHeaders buildHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private static Map<String, Object> buildRequestBody(String modelName, AiProviderPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("instructions", prompt.systemInstruction());
        body.put("input", buildInput(prompt));
        body.put("max_output_tokens", prompt.maxOutputTokens());
        // Learner prompts are already persisted locally where needed; the provider does not need to keep them.
        body.put("store", false);
        if (prompt.expectJson()) {
            body.put("text", Map.of("format", Map.of("type", "json_object")));
        }
        return body;
    }

    private static List<Map<String, Object>> buildInput(AiProviderPrompt prompt) {
        List<Map<String, Object>> input = new ArrayList<>(prompt.messageHistory().size() + 1);
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            String role = ROLE_ASSISTANT.equalsIgnoreCase(message.role()) ? ROLE_ASSISTANT : ROLE_USER;
            input.add(Map.of("role", role, "content", message.content()));
        }
        if (prompt.hasImage()) {
            // Multimodal turn: bounded text instruction plus the learner image as a data URL.
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "input_text", "text", prompt.userPrompt()),
                    Map.of("type", "input_image", "image_url", prompt.image().toDataUrl(), "detail", IMAGE_DETAIL));
            input.add(Map.of("role", ROLE_USER, "content", content));
        } else {
            input.add(Map.of("role", ROLE_USER, "content", prompt.userPrompt()));
        }
        return input;
    }

    private static AiProviderResult parseResponse(
            String providerName,
            String modelName,
            Map<String, Object> responseBody,
            boolean expectJson) {
        if (responseBody == null) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider returned an empty response body.");
        }

        Object status = responseBody.get("status");
        boolean completed = status == null || STATUS_COMPLETED.equals(status);
        boolean incomplete = STATUS_INCOMPLETE.equals(status);
        if (!completed && !incomplete) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response status was not completed.");
        }
        // A truncated JSON document cannot be parsed safely, so incomplete JSON output is rejected.
        if (incomplete && expectJson) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response was incomplete.");
        }

        String text = extractOutputText(responseBody.get("output"));
        if (text == null || text.isBlank()) {
            return AiProviderResult.failure(providerName, modelName,
                    "Provider response text was empty.");
        }

        Integer tokenCount = extractTokenCount(responseBody.get("usage"));
        return AiProviderResult.success(providerName, modelName, text.trim(), tokenCount);
    }

    private static String extractOutputText(Object outputObject) {
        if (!(outputObject instanceof List<?> outputItems)) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Object item : outputItems) {
            if (!(item instanceof Map<?, ?> itemMap) || !"message".equals(itemMap.get("type"))) {
                continue;
            }
            if (!(itemMap.get("content") instanceof List<?> contentParts)) {
                continue;
            }
            for (Object part : contentParts) {
                if (part instanceof Map<?, ?> partMap
                        && "output_text".equals(partMap.get("type"))
                        && partMap.get("text") instanceof String partText) {
                    text.append(partText);
                }
            }
        }
        return text.toString();
    }

    private String resolveEndpoint() {
        String raw = TextUtils.trimToNull(aiProperties.getApiBaseUrl());
        String baseUrl = AiProviderSupport.validateAndNormalizeBaseUrl(
                raw != null ? raw : DEFAULT_BASE_URL);
        return baseUrl != null ? baseUrl + RESPONSES_PATH : null;
    }

    private static Integer extractTokenCount(Object usageObject) {
        if (!(usageObject instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof BigInteger bigInteger) {
            return toNonNegativeInt(bigInteger);
        }
        if (totalTokens instanceof BigDecimal bigDecimal) {
            try {
                return toNonNegativeInt(bigDecimal.toBigIntegerExact());
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

    private static Integer toNonNegativeInt(BigInteger value) {
        return value.signum() >= 0 && value.compareTo(INTEGER_MAX_VALUE) <= 0
                ? value.intValue()
                : null;
    }
}
