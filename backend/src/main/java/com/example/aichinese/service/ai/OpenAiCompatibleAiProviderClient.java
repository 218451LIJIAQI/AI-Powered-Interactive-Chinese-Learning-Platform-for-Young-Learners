package com.example.aichinese.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;

/**
 * The Chat Completions client ({@code POST /chat/completions}) of the alternative providers ({@code deepseek},
 * {@code openai-compatible}): text only, the instructions sent as the first, {@code system} message.
 */
public class OpenAiCompatibleAiProviderClient extends HttpAiProviderClient {

    public OpenAiCompatibleAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        super(aiProperties, restTemplateBuilder, "/chat/completions");
    }

    @Override
    Map<String, Object> buildRequestBody(String modelName, int maxOutputTokens, AiProviderPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", buildMessages(prompt));
        body.put("max_tokens", maxOutputTokens);
        body.put("temperature", 0.7);
        if (prompt.expectJson()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private static List<Map<String, Object>> buildMessages(AiProviderPrompt prompt) {
        List<Map<String, Object>> messages = new ArrayList<>(prompt.messageHistory().size() + 2);
        messages.add(message("system", prompt.systemInstruction()));
        for (AiProviderPrompt.AiMessage message : prompt.messageHistory()) {
            messages.add(message(message.role(), message.content()));
        }
        messages.add(message("user", prompt.userPrompt()));
        return messages;
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    @Override
    AiProviderResult parseResponse(Map<String, Object> responseBody, AiProviderPrompt prompt) {
        Integer tokens = extractTokenCount(responseBody.get("usage"));
        if (!(responseBody.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return AiProviderResult.failure("Provider response did not contain any choices.", tokens);
        }
        if (!(choices.get(0) instanceof Map<?, ?> choiceMap)) {
            return AiProviderResult.failure("Provider response choice format was invalid.", tokens);
        }
        Object finishReason = choiceMap.get("finish_reason");
        if ("content_filter".equals(finishReason)) {
            return AiProviderResult.failure(FILTERED_ERROR, tokens);
        }
        if ("length".equals(finishReason) && prompt.expectJson()) {
            return AiProviderResult.failure(INCOMPLETE_ERROR, tokens);
        }
        if (!(choiceMap.get("message") instanceof Map<?, ?> messageMap)) {
            return AiProviderResult.failure("Provider response message format was invalid.", tokens);
        }
        return textResult(messageMap.get("content") instanceof String text ? text : null, tokens);
    }
}
