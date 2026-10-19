package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.SensitiveDataRedactor;

class SensitiveDataRedactorTests {

    private static final String TEXT_REDACTED = "[REDACTED]";
    private static final String MAP_REDACTED = SensitiveDataRedactor.FILTERED;
    private static final String EMAIL = "learner@example.com";
    private static final String STANDALONE_EMAIL = "standalone@example.com";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String PASSWORD = "Secret123";
    private static final String RAW_TOKEN = "raw-token";
    private static final String CLIENT_SECRET = "client-secret-value";
    private static final String API_KEY = "sk-live";
    private static final String RAW_BEARER = "raw-bearer";
    private static final String RAW_CLIENT_SECRET = "raw-client-secret";
    private static final String RESET_PASSWORD_URL =
            "https://learn.example/reset-password?token=" + RAW_TOKEN + "&safe=true";
    private static final String RIGHT_TO_LEFT_OVERRIDE = "\u202E";

    @Test
    void redactTextCoversSecretsEmailsBearerTokensAndLayoutControl() {
        String value = """
                email=%s Authorization: Bearer %s password=%s
                resetLink=%s
                client_secret=%s {"apiKey":"%s"} %s %s
                """.formatted(
                EMAIL,
                ACCESS_TOKEN,
                PASSWORD,
                RESET_PASSWORD_URL,
                CLIENT_SECRET,
                API_KEY,
                STANDALONE_EMAIL,
                RIGHT_TO_LEFT_OVERRIDE);

        String redacted = SensitiveDataRedactor.redactText(value);

        assertThat(redacted)
                .contains("email=" + TEXT_REDACTED)
                .contains("Authorization: " + TEXT_REDACTED)
                .contains("password=" + TEXT_REDACTED)
                .contains("resetLink=" + TEXT_REDACTED)
                .contains("client_secret=" + TEXT_REDACTED)
                .contains("\"apiKey\":\"" + TEXT_REDACTED + "\"");
        assertThat(redacted)
                .doesNotContain(EMAIL)
                .doesNotContain(ACCESS_TOKEN)
                .doesNotContain(PASSWORD)
                .doesNotContain(RAW_TOKEN)
                .doesNotContain(CLIENT_SECRET)
                .doesNotContain(API_KEY)
                .doesNotContain(STANDALONE_EMAIL)
                .doesNotContain(RIGHT_TO_LEFT_OVERRIDE);
    }

    @Test
    void redactTextPreservesSafeQueryParametersWhenOnlyOneParameterIsSensitive() {
        String redacted = SensitiveDataRedactor.redactText(RESET_PASSWORD_URL);

        assertThat(redacted)
                .contains("token=" + TEXT_REDACTED)
                .contains("safe=true")
                .doesNotContain(RAW_TOKEN);
    }

    @Test
    void redactMapRecursesThroughNestedObjectsAndHandlesCycles() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("safe", "hello");
        cyclic.put("nested", Map.of("token", RAW_TOKEN, "message", "Bearer " + RAW_BEARER));
        cyclic.put("list", List.of(EMAIL, Map.of("clientSecret", RAW_CLIENT_SECRET)));
        cyclic.put("self", cyclic);

        Map<String, Object> redacted = SensitiveDataRedactor.redactMap(cyclic, MAP_REDACTED);

        assertThat(redacted).containsEntry("safe", "hello");
        assertThat(redacted.get("nested"))
                .isEqualTo(Map.of("token", MAP_REDACTED, "message", "Bearer " + MAP_REDACTED));
        assertThat(redacted.get("list").toString())
                .contains(MAP_REDACTED)
                .doesNotContain(EMAIL)
                .doesNotContain(RAW_CLIENT_SECRET);
        assertThat(redacted).containsEntry("self", MAP_REDACTED);
    }
}
