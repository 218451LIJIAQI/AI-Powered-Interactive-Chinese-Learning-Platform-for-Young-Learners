package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.SensitiveDataRedactor;

class SensitiveDataRedactorTests {

    @Test
    void redactTextCoversSecretsEmailsBearerTokensAndLayoutControl() {
        String value = """
                email=parent@example.com Authorization: Bearer access-token password=Secret123
                resetLink=https://learn.example/reset-password?token=raw-token&safe=true
                client_secret=client-secret-value {"apiKey":"sk-live"} standalone@example.com \u202E
                """;

        String redacted = SensitiveDataRedactor.redactText(value);

        assertThat(redacted)
                .contains("email=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .contains("password=[REDACTED]")
                .contains("resetLink=[REDACTED]")
                .contains("client_secret=[REDACTED]")
                .contains("\"apiKey\":\"[REDACTED]\"");
        assertThat(redacted)
                .doesNotContain("parent@example.com")
                .doesNotContain("access-token")
                .doesNotContain("Secret123")
                .doesNotContain("raw-token")
                .doesNotContain("client-secret-value")
                .doesNotContain("sk-live")
                .doesNotContain("standalone@example.com")
                .doesNotContain("\u202E");
    }

    @Test
    void redactTextPreservesSafeQueryParametersWhenOnlyOneParameterIsSensitive() {
        String redacted = SensitiveDataRedactor.redactText("https://learn.example/reset-password?token=raw-token&safe=true");

        assertThat(redacted)
                .contains("token=[REDACTED]")
                .contains("safe=true")
                .doesNotContain("raw-token");
    }

    @Test
    void redactMapRecursesThroughNestedObjectsAndHandlesCycles() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("safe", "hello");
        cyclic.put("nested", Map.of("token", "raw-token", "message", "Bearer raw-bearer"));
        cyclic.put("list", List.of("parent@example.com", Map.of("clientSecret", "raw-client-secret")));
        cyclic.put("self", cyclic);

        Map<String, Object> redacted = SensitiveDataRedactor.redactMap(cyclic, SensitiveDataRedactor.FILTERED);

        assertThat(redacted).containsEntry("safe", "hello");
        assertThat(redacted.get("nested")).isEqualTo(Map.of("token", "[Filtered]", "message", "Bearer [Filtered]"));
        assertThat(redacted.get("list").toString())
                .contains("[Filtered]")
                .doesNotContain("parent@example.com")
                .doesNotContain("raw-client-secret");
        assertThat(redacted).containsEntry("self", "[Filtered]");
    }
}
