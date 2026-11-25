package com.example.aichinese.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;

/**
 * Default AI provider client for the prototype. It calls the OpenAI Responses API
 * ({@code POST /v1/responses}) with the configured model (GPT-5.6 Luna by default),
 * as specified in the project proposal. Bounded-AI instructions are sent through the
 * {@code instructions} field, response storage for later retrieval is disabled ({@code store=false}),
 * and any provider error is converted into a controlled failure result.
 */
public class OpenAiResponsesAiProviderClient extends HttpAiProviderClient {

    private static final String ROLE_USER = "user";
    /** Low detail keeps image token usage bounded; a single handwritten character does not need more. */
    private static final String IMAGE_DETAIL = "low";

    public OpenAiResponsesAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        super(aiProperties, restTemplateBuilder, "/responses");
    }

    @Override
    Map<String, Object> buildRequestBody(String modelName, int maxOutputTokens, AiProviderPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("instructions", prompt.systemInstruction());
        body.put("input", buildInput(prompt));
        body.put("max_output_tokens", maxOutputTokens);
        // Disable response retrieval storage; provider safety retention is governed separately.
        body.put("store", false);
        if (prompt.expectJson()) {
            body.put("text", Map.of("format", Map.of("type", "json_object")));
        }
        return body;
    }

    private static List<Map<String, Object>> buildInput(AiProviderPrompt prompt) {
        List<Map<String, Object>> input = new ArrayList<>(prompt.messageHistory().size() + 2);
        if (prompt.expectJson()) {
            // Responses JSON mode requires an explicit JSON instruction inside the input messages.
            input.add(Map.of("role", "developer", "content", "Return a JSON object."));
        }
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            input.add(Map.of("role", message.role(), "content", message.content()));
        }
        if (prompt.image() != null) {
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

    @Override
    AiProviderResult parseResponse(Map<String, Object> responseBody, AiProviderPrompt prompt) {
        Object status = responseBody.get("status");
        Integer tokens = extractTokenCount(responseBody.get("usage"));
        boolean completed = status == null || "completed".equals(status);
        boolean incomplete = "incomplete".equals(status);
        if (!completed && !incomplete) {
            return AiProviderResult.failure("Provider response status was not completed.", tokens);
        }
        if (incomplete) {
            Object reason = responseBody.get("incomplete_details") instanceof Map<?, ?> details
                    ? details.get("reason")
                    : null;
            if ("content_filter".equals(reason)) {
                return AiProviderResult.failure(FILTERED_ERROR, tokens);
            }
            // A truncated JSON document cannot be parsed safely, so incomplete JSON output is rejected.
            if (prompt.expectJson()) {
                return AiProviderResult.failure(INCOMPLETE_ERROR, tokens);
            }
        }
        return textResult(extractOutputText(responseBody.get("output")), tokens);
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
}
