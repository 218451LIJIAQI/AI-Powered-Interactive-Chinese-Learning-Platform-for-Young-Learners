package com.example.aichinese.service.ai;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.aichinese.config.AiProperties;

/**
 * The provider client {@code AI_PROVIDER} names: the OpenAI Responses API by default, Chat Completions for the
 * alternatives. An unsupported name is reported by {@link AiAvailabilityService}, so no request reaches the client.
 */
@Configuration(proxyBeanMethods = false)
public class AiProviderConfig {

    @Bean
    public AiProviderClient aiProviderClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        return AiProperties.OPENAI.equals(aiProperties.normalizedProvider())
                ? new OpenAiResponsesAiProviderClient(aiProperties, restTemplateBuilder)
                : new OpenAiCompatibleAiProviderClient(aiProperties, restTemplateBuilder);
    }
}
