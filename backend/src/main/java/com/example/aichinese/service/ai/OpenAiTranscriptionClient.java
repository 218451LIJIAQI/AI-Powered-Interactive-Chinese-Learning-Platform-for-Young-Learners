package com.example.aichinese.service.ai;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

/**
 * Writes down a recording with the OpenAI speech-to-text endpoint ({@code POST /audio/transcriptions}) in Mandarin.
 * The target text is deliberately not sent as a hint, so the transcript shows what was actually said. Like the text
 * clients, any provider error becomes a controlled failure result.
 */
@Component
public class OpenAiTranscriptionClient implements AiTranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final String MANDARIN = "zh";

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate;

    public OpenAiTranscriptionClient(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        this.aiProperties = aiProperties;
        Duration timeout = Duration.ofSeconds(aiProperties.getTimeoutSeconds());
        this.restTemplate = restTemplateBuilder.connectTimeout(timeout).readTimeout(timeout).build();
    }

    @Override
    public AiProviderResult transcribe(AiAudioInput audio) {
        String model = TextUtils.trimToNull(aiProperties.getTranscriptionModel());
        String apiKey = TextUtils.trimToNull(aiProperties.getApiKey());
        String baseUrl = AiProviderSupport.validateAndNormalizeBaseUrl(aiProperties.getApiBaseUrl());
        if (model == null || apiKey == null || baseUrl == null) {
            return AiProviderResult.failure("Speech-to-text is not configured.");
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", model);
        form.add("language", MANDARIN);
        form.add("response_format", "json");
        form.add("file", new ByteArrayResource(audio.data()) {
            @Override
            public String getFilename() {
                return audio.fileName();
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);
        try {
            Map<String, Object> body = restTemplate.exchange(
                    baseUrl + "/audio/transcriptions", HttpMethod.POST, new HttpEntity<>(form, headers), MAP_RESPONSE_TYPE)
                    .getBody();
            Object text = body == null ? null : body.get("text");
            String transcript = text == null ? "" : String.valueOf(text).strip();
            return transcript.isEmpty()
                    ? AiProviderResult.failure(NOTHING_HEARD)
                    : AiProviderResult.success(transcript, AiProviderSupport.extractTokenCount(body.get("usage")));
        } catch (RuntimeException exception) {
            log.warn("Speech-to-text request failed with exception type {}.", exception.getClass().getSimpleName());
            return AiProviderResult.failure("Provider request failed or timed out.");
        }
    }
}
