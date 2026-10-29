package com.example.aichinese.config;

import static com.example.aichinese.support.ConfigFixtures.environment;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit coverage for {@code AiProductionGuard}: pins the fail-fast
 * behaviour: production requires the real-provider toggle, a configured
 * API key, a supported provider and an HTTPS base URL; any missing piece
 * aborts startup so production never silently ships unavailable AI.
 */
class AiProductionGuardTests {

    static Stream<Arguments> productionMisconfigurations() {
        return Stream.of(
                misconfiguration("real provider disabled",
                        properties -> properties.setEnableRealProvider(false), "AI_ENABLE_REAL_PROVIDER"),
                misconfiguration("unsupported provider",
                        properties -> properties.setProvider("unknown-provider"), "AI_PROVIDER"),
                misconfiguration("blank API key", properties -> properties.setApiKey(" "), "AI_API_KEY"),
                misconfiguration("blank model name", properties -> properties.setModelName(" "), "AI_MODEL_NAME"),
                misconfiguration("placeholder API key",
                        properties -> properties.setApiKey("replace-with-your-deepseek-api-key"), "placeholder"),
                misconfiguration("HTTP base URL",
                        properties -> properties.setApiBaseUrl("http://api.deepseek.com"), "HTTPS"),
                misconfiguration("loopback base URL", properties -> {
                    properties.setProvider("openai_compatible");
                    properties.setApiBaseUrl("https://localhost:11434");
                }, "non-loopback public host"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("productionMisconfigurations")
    void prodProfileRejectsAnIncompleteOrUnsafeRealProviderConfiguration(
            Consumer<AiProperties> misconfiguration, String expectedMessage) {
        AiProperties properties = configuredProperties();
        misconfiguration.accept(properties);

        assertThatThrownBy(() -> new AiProductionGuard(environment("prod"), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void prodProfileAllowsProductionReadyRealProviderConfiguration() {
        assertThatCode(() -> new AiProductionGuard(environment("prod"), configuredProperties()))
                .doesNotThrowAnyException();
    }

    private static Arguments misconfiguration(String name, Consumer<AiProperties> change, String expectedMessage) {
        return arguments(named(name, change), expectedMessage);
    }

    private static AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnableRealProvider(true);
        properties.setProvider("deepseek");
        properties.setApiBaseUrl("https://api.deepseek.com");
        properties.setApiKey("live-ai-key");
        properties.setModelName("deepseek-chat");
        return properties;
    }
}
