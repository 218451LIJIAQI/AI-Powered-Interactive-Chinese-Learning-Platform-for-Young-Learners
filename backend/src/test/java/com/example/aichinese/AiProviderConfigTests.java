package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.service.ai.AiProviderClient;
import com.example.aichinese.service.ai.AiProviderConfig;
import com.example.aichinese.service.ai.OpenAiCompatibleAiProviderClient;
import com.example.aichinese.service.ai.OpenAiResponsesAiProviderClient;

/**
 * The default "openai" provider uses the OpenAI Responses API client named in the proposal; alternative Chat
 * Completions providers use the compatible client. An unknown name is rejected by the availability check and the
 * production guard ({@code OpenAiCompatibleAiProviderClientTests}, {@code AiProductionGuardTests}).
 */
class AiProviderConfigTests {

    @Test
    void defaultProviderUsesOpenAiResponsesClient() {
        assertThat(client(null)).isInstanceOf(OpenAiResponsesAiProviderClient.class);
        assertThat(client("openai")).isInstanceOf(OpenAiResponsesAiProviderClient.class);
    }

    @Test
    void alternativeProvidersUseOpenAiCompatibleClient() {
        assertThat(client("deepseek")).isInstanceOf(OpenAiCompatibleAiProviderClient.class);
        assertThat(client("openai-compatible")).isInstanceOf(OpenAiCompatibleAiProviderClient.class);
    }

    @Test
    void configurationDiagnosticsOmitCredentialsEvenInAMisconfiguredUrl() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("test-key");
        properties.setApiBaseUrl("https://example.test/api?token=private-value");

        assertThat(properties.toString()).contains("provider=openai").doesNotContain("test-key", "private-value");
    }

    @Test
    void privateKeyFileCanBeFilledRotatedAndClearedWithoutRestarting(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("openai-api-key.txt");
        AiProperties properties = new AiProperties();
        properties.setApiKeyFile(file);
        assertThat(properties.getApiKey()).isNull();

        Files.writeString(file, "\uFEFFfirst-test-key\r\n");
        assertThat(properties.getApiKey()).isEqualTo("first-test-key");
        assertThat(properties.toString()).doesNotContain("first-test-key");
        Files.writeString(file, "second-test-key");
        assertThat(properties.getApiKey()).isEqualTo("second-test-key");

        properties.setApiKey("explicit-test-key");
        assertThat(properties.getApiKey()).isEqualTo("explicit-test-key");
        properties.setApiKey("malformed\nkey");
        assertThat(properties.getApiKey()).isNull();
        properties.setApiKey("key with spaces");
        assertThat(properties.getApiKey()).isNull();
        properties.setApiKey(null);
        Files.writeString(file, "two\nlines");
        assertThat(properties.getApiKey()).isNull();
        Files.writeString(file, "  \r\n");
        assertThat(properties.getApiKey()).isNull();
    }

    private static AiProviderClient client(String provider) {
        AiProperties properties = new AiProperties();
        properties.setProvider(provider);
        return new AiProviderConfig().aiProviderClient(properties, new RestTemplateBuilder());
    }
}
