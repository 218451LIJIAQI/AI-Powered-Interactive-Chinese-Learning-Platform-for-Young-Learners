package com.example.aichinese.service.ai;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;

/**
 * The HTTP call shared by the OpenAI-style provider clients: the configuration check, one bearer-authenticated
 * JSON POST to the configured base URL plus {@code path} within the configured AI timeout, and a controlled failure
 * result instead of an exception for anything that goes wrong. Subclasses build the request body and find the reply
 * text in the response body ({@link #textResult}).
 */
abstract class HttpAiProviderClient implements AiProviderClient {

    private static final String EMPTY_BODY_ERROR = "Provider returned an empty response body.";
    static final String FILTERED_ERROR = "Provider response was filtered.";
    static final String INCOMPLETE_ERROR = "Provider response was incomplete.";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final AiProperties aiProperties;
    private final String path;
    /**
     * The most one provider call may take in total. Connect and read timeouts alone do not bound it (a slow connection
     * plus a reply that trickles in can each stay just under them), so {@link #post} also gives up at this deadline:
     * the caller (the chat stream, the browser) must never still be waiting when the server stops.
     */
    private final Duration deadline;
    private final RestTemplate restTemplate;
    private final ExecutorService callExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ai-provider-call-", 0).factory());

    HttpAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder, String path) {
        this.aiProperties = aiProperties;
        this.path = path;
        this.deadline = Duration.ofSeconds(aiProperties.getTimeoutSeconds());
        // Connect and read timeouts both use the configured AI timeout too (SDD 5.6); never follow 3xx redirects so
        // bearer credentials cannot be forwarded to an unvalidated host, and treat any non-2xx status (including 3xx)
        // as an HTTP failure so it logs and records its status code instead of reporting an empty response body.
        this.restTemplate = restTemplateBuilder
                .connectTimeout(deadline)
                .readTimeout(deadline)
                .redirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW)
                .errorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    protected boolean hasError(HttpStatusCode statusCode) {
                        return !statusCode.is2xxSuccessful();
                    }
                })
                .build();
    }

    @PreDestroy
    void close() {
        callExecutor.shutdownNow();
    }

    abstract Map<String, Object> buildRequestBody(String modelName, int maxOutputTokens, AiProviderPrompt prompt);

    /** Reads a non-null response body. */
    abstract AiProviderResult parseResponse(Map<String, Object> responseBody, AiProviderPrompt prompt);

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        String modelName = aiProperties.getModelName();
        String apiKey = aiProperties.getApiKey();
        String baseUrl = aiProperties.normalizedApiBaseUrl();
        if (modelName == null || apiKey == null || baseUrl == null) {
            return AiProviderResult.failure("AI provider is not configured.");
        }

        try {
            Map<String, Object> responseBody = post(callExecutor, restTemplate, deadline, baseUrl + path, apiKey,
                    buildRequestBody(modelName, aiProperties.getMaxOutputTokens(), prompt));
            if (responseBody == null) {
                return AiProviderResult.failure(EMPTY_BODY_ERROR);
            }
            return parseResponse(responseBody, prompt);
        } catch (RuntimeException ex) {
            String summary = failureSummary(ex);
            if (Thread.currentThread().isInterrupted()) {
                log.debug("AI provider request cancelled for feature {} ({})",
                        prompt.featureKey(), ex.getClass().getSimpleName());
            } else {
                log.warn("AI provider request failed for feature {} with exception type {}: {}",
                        prompt.featureKey(), ex.getClass().getSimpleName(), summary);
            }
            return AiProviderResult.failure(summary);
        }
    }

    /**
     * POSTs {@code body} as JSON with the API key as bearer token; the reply's JSON object, or null for an empty body.
     * The call runs on {@code executor} and is abandoned (interrupted, which closes its connection) at {@code deadline};
     * then a {@link ResourceAccessException} reports the timeout like any other connection failure.
     */
    static Map<String, Object> post(
            ExecutorService executor,
            RestTemplate restTemplate,
            Duration deadline,
            String url,
            String apiKey,
            Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        Future<Map<String, Object>> call =
                executor.submit(() -> restTemplate.exchange(url, HttpMethod.POST, request, JSON_OBJECT).getBody());
        try {
            return call.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            call.cancel(true);
            throw new ResourceAccessException("AI provider call exceeded its " + deadline.toSeconds() + " s deadline.");
        } catch (InterruptedException interrupted) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("AI provider call was interrupted.");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failed.getCause() instanceof Error error) {
                throw error;
            }
            throw new ResourceAccessException("AI provider call failed.");
        }
    }

    /**
     * The usage-log summary of a failed call: the HTTP status the provider answered with (a wrong API key, an unknown
     * model, the provider's own rate limit), otherwise {@link AiGateway#PROVIDER_FAILED_ERROR} for a connection
     * failure or the deadline. Never the response body, which can echo part of the key.
     */
    private static String failureSummary(RuntimeException failure) {
        return failure instanceof RestClientResponseException response
                ? "Provider answered with HTTP " + response.getStatusCode().value() + "."
                : AiGateway.PROVIDER_FAILED_ERROR;
    }

    /** The reply text as the result: a failure when it is missing or blank, otherwise stripped, with its token count. */
    static AiProviderResult textResult(String text, Integer tokens) {
        if (text == null || text.isBlank()) {
            return AiProviderResult.failure(AiGateway.EMPTY_REPLY_ERROR, tokens);
        }
        return AiProviderResult.success(text.strip(), tokens);
    }

    /** The reply's {@code usage.total_tokens}, when it is a non-negative integer that fits the usage log. */
    static Integer extractTokenCount(Object usageObject) {
        if (usageObject instanceof Map<?, ?> usageMap && usageMap.get("total_tokens") instanceof Number tokens) {
            double tokenCount = tokens.doubleValue();
            if (tokenCount >= 0 && tokenCount <= Integer.MAX_VALUE && tokenCount == Math.rint(tokenCount)) {
                return tokens.intValue();
            }
        }
        return null;
    }
}
