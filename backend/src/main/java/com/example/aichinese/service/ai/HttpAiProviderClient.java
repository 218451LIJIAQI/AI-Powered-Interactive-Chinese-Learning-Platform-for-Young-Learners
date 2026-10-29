package com.example.aichinese.service.ai;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

/**
 * The HTTP call shared by the OpenAI-style provider clients: the configuration check, one bearer-authenticated
 * JSON POST to the configured base URL plus {@code path}, and a controlled failure result instead of an exception
 * for anything that goes wrong. Subclasses build the request body and read the response body.
 */
abstract class HttpAiProviderClient implements AiProviderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;
    private final String path;

    HttpAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder, String path) {
        this.aiProperties = aiProperties;
        // Connect and read timeouts both use the configured AI timeout (SDD 5.6).
        Duration timeout = Duration.ofSeconds(aiProperties.getTimeoutSeconds());
        this.restTemplate = restTemplateBuilder.connectTimeout(timeout).readTimeout(timeout).build();
        this.path = path;
    }

    abstract Map<String, Object> buildRequestBody(String modelName, AiProviderPrompt prompt);

    /** Reads a non-null response body. */
    abstract AiProviderResult parseResponse(Map<String, Object> responseBody, AiProviderPrompt prompt);

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        String modelName = TextUtils.trimToNull(aiProperties.getModelName());
        String apiKey = TextUtils.trimToNull(aiProperties.getApiKey());
        String baseUrl = AiProviderSupport.validateAndNormalizeBaseUrl(aiProperties.getApiBaseUrl());
        if (modelName == null) {
            return AiProviderResult.failure("AI model name is missing.");
        }
        if (apiKey == null) {
            return AiProviderResult.failure("AI API key is missing.");
        }
        if (baseUrl == null) {
            return AiProviderResult.failure("AI API base URL is missing or invalid.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildRequestBody(modelName, prompt), headers);
        try {
            Map<String, Object> responseBody =
                    restTemplate.exchange(baseUrl + path, HttpMethod.POST, request, MAP_RESPONSE_TYPE).getBody();
            if (responseBody == null) {
                return AiProviderResult.failure("Provider returned an empty response body.");
            }
            return parseResponse(responseBody, prompt);
        } catch (RuntimeException ex) {
            log.warn("AI provider request failed for feature {} with exception type {}.",
                    prompt.featureKey(), ex.getClass().getSimpleName());
            return AiProviderResult.failure("Provider request failed or timed out.");
        }
    }
}
