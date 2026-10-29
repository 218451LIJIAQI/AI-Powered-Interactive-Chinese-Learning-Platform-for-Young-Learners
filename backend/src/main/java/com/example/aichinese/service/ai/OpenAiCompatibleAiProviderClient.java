package com.example.aichinese.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

@Component
public class OpenAiCompatibleAiProviderClient extends HttpAiProviderClient {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final double DEFAULT_TEMPERATURE = 0.7;

    public OpenAiCompatibleAiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        super(aiProperties, restTemplateBuilder, "/chat/completions");
    }

    @Override
    Map<String, Object> buildRequestBody(String modelName, AiProviderPrompt prompt) {
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

    @Override
    AiProviderResult parseResponse(Map<String, Object> responseBody, AiProviderPrompt prompt) {
        Object choicesObject = responseBody.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            return AiProviderResult.failure("Provider response did not contain any choices.");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return AiProviderResult.failure("Provider response choice format was invalid.");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            return AiProviderResult.failure("Provider response message format was invalid.");
        }

        Object content = messageMap.get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            return AiProviderResult.failure("Provider response text was empty.");
        }

        Integer tokenCount = AiProviderSupport.extractTokenCount(responseBody.get("usage"));
        return AiProviderResult.success(text.trim(), tokenCount);
    }
}
