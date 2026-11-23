package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.SensitiveDataRedactor;

class SensitiveDataRedactorTests {

    private static final String TEXT_REDACTED = SensitiveDataRedactor.REDACTED;
    private static final String EMAIL = "learner@example.com";
    private static final String STANDALONE_EMAIL = "standalone@example.com";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String PASSWORD = "Secret123";
    private static final String RAW_TOKEN = "raw-token";
    private static final String CLIENT_SECRET = "client-secret-value";
    private static final String API_KEY = "sk-live";
    private static final String RAW_BEARER = "raw-bearer";
    private static final String RESET_PASSWORD_URL =
            "https://learn.example/reset-password?token=" + RAW_TOKEN + "&safe=true";
    private static final String RIGHT_TO_LEFT_OVERRIDE = "\u202E";

    @Test
    void theWordsBearerTokensAreNotACredentialButABearerValueIs() {
        String redacted = SensitiveDataRedactor.redactText(
                "User signed out and invalidated existing bearer tokens. Header was Bearer " + RAW_BEARER);

        assertThat(redacted)
                .startsWith("User signed out and invalidated existing bearer tokens.")
                .doesNotContain(RAW_BEARER);
    }

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
                .contains("email=" + TEXT_REDACTED, "Authorization: " + TEXT_REDACTED,
                        "password=" + TEXT_REDACTED, "resetLink=" + TEXT_REDACTED,
                        "client_secret=" + TEXT_REDACTED, "\"apiKey\":\"" + TEXT_REDACTED + "\"")
                .doesNotContain(EMAIL, ACCESS_TOKEN, PASSWORD, RAW_TOKEN, CLIENT_SECRET, API_KEY,
                        STANDALONE_EMAIL, RIGHT_TO_LEFT_OVERRIDE);
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
    void redactTextCoversPrefixedJsonTokensApostrophesInJsonSecretsAndQuotedAssignments() {
        String value = """
                {"access_token":"eyJ-secret","refreshToken":"rt-secret","authorization":"Basic dXNlcjpwYXNz","password":"don't-leak"}
                password="QuotedSecret123" api_key='sk-quoted-live'
                """;

        String redacted = SensitiveDataRedactor.redactText(value);

        assertThat(redacted)
                .contains("\"access_token\":\"" + TEXT_REDACTED + "\"",
                        "\"refreshToken\":\"" + TEXT_REDACTED + "\"",
                        "\"authorization\":\"" + TEXT_REDACTED + "\"",
                        "\"password\":\"" + TEXT_REDACTED + "\"",
                        "password=" + TEXT_REDACTED,
                        "api_key=" + TEXT_REDACTED)
                .doesNotContain("eyJ-secret", "rt-secret", "dXNlcjpwYXNz", "don't-leak",
                        "QuotedSecret123", "sk-quoted-live");
    }

    @Test
    void redactTextCoversMultilineQuotedSecretsNonBearerAuthSchemesAndUnclosedQuotesLinearly() {
        String multiline = "password=\"secret1\nsecret2\" Authorization: Basic dXNlcjpwYXNz Authorization: ApiKey sk-secret-99";
        String redacted = SensitiveDataRedactor.redactText(multiline);
        assertThat(redacted)
                .contains("password=" + TEXT_REDACTED, "Authorization: " + TEXT_REDACTED)
                .doesNotContain("secret1", "secret2", "dXNlcjpwYXNz", "sk-secret-99");

        // Unclosed/truncated quoted secrets must still be redacted instead of leaking verbatim.
        assertThat(SensitiveDataRedactor.redactText("password=\"Secret123"))
                .isEqualTo("password=" + TEXT_REDACTED);
        assertThat(SensitiveDataRedactor.redactText("{\"access_token\":\"eyJhbGci.truncated"))
                .contains("\"access_token\":\"" + TEXT_REDACTED + "\"")
                .doesNotContain("eyJhbGci.truncated");
        assertThat(SensitiveDataRedactor.redactText("Authorization: \"Bearer eyJ.payload.sig"))
                .isEqualTo("Authorization: " + TEXT_REDACTED);

        // Whitespace-only ASCII control characters must still be stripped rather than bypassed by isBlank().
        assertThat(SensitiveDataRedactor.redactText("\u000B\f\u001F")).isEqualTo("   ");

        // Unclosed quote followed by many backslashes must finish in linear time without backtracking stall.
        String adversarial = "password=\"" + "\\".repeat(1000);
        long startNanos = System.nanoTime();
        SensitiveDataRedactor.redactText(adversarial);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(1_000L);
    }

    @Test
    void redactTextCoversCompoundCredentialKeysCookiesAndFinishesLinearlyOnAdversarialEmailInputs() {
        String value = """
                {"currentPassword":"OldSecret1","newPassword":"NewSecret2","confirmPassword":"NewSecret2","secretKey":"tc-key","secretId":"tc-id","x-api-key":"sk-hdr"}
                currentPassword=OldSecret1 secretKey=tc-key
                Cookie: mandarin_spark_refresh=rt-cookie-123; theme=light
                Set-Cookie: mandarin_spark_refresh=rt-cookie-456; HttpOnly; Path=/
                """;
        String redacted = SensitiveDataRedactor.redactText(value);
        assertThat(redacted)
                .doesNotContain("OldSecret1", "NewSecret2", "tc-key", "tc-id", "sk-hdr",
                        "rt-cookie-123", "rt-cookie-456")
                .contains("\"currentPassword\":\"" + TEXT_REDACTED + "\"",
                        "\"secretKey\":\"" + TEXT_REDACTED + "\"",
                        "\"x-api-key\":\"" + TEXT_REDACTED + "\"",
                        "Cookie: " + TEXT_REDACTED,
                        "Set-Cookie: " + TEXT_REDACTED);

        long startNanos = System.nanoTime();
        assertThat(SensitiveDataRedactor.redactText(".a".repeat(15_000))).hasSize(30_000);
        assertThat(SensitiveDataRedactor.redactText("a@" + "a.".repeat(15_000) + "!")).hasSize(30_003);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(1_000L);
    }

    @Test
    void redactTextCoversDigestHmacHeadersQuotedCookiesNumericAndEscapedJsonSecrets() {
        String value = """
                Authorization: Digest username="alice", nonce="n123", response="deadbeef"
                Authorization: TC3-HMAC-SHA256 Credential=akid/2026, Signature=sigsecret99
                Cookie: "theme"=light; mandarin_spark_refresh=rt-trailing-cookie
                {"passcode": 123456}
                {\\"password\\":\\"EscapedSecret42\\"}
                """;
        String redacted = SensitiveDataRedactor.redactText(value);
        assertThat(redacted)
                .doesNotContain("n123", "deadbeef", "sigsecret99", "rt-trailing-cookie", "123456", "EscapedSecret42")
                .contains("Authorization: " + TEXT_REDACTED, "Cookie: " + TEXT_REDACTED,
                        "\"passcode\": \"" + TEXT_REDACTED + "\"",
                        "{\\\"password\\\":\\\"" + TEXT_REDACTED + "\\\"}");
    }

    @Test
    void redactTextPreservesEscapedJsonAndCoversInnerQuotesAndTrailingBackslashes() {
        String withInnerQuote = "{\\\"password\\\":\\\"abc\\\\\\\"def\\\",\\\"safe\\\":\\\"ok\\\"}";
        assertThat(SensitiveDataRedactor.redactText(withInnerQuote))
                .isEqualTo("{\\\"password\\\":\\\"" + TEXT_REDACTED + "\\\",\\\"safe\\\":\\\"ok\\\"}");

        String withTrailingBackslash = "{\\\"apiKey\\\":\\\"secret\\\\\\\\\\\",\\\"safe\\\":\\\"keep\\\"}";
        assertThat(SensitiveDataRedactor.redactText(withTrailingBackslash))
                .isEqualTo("{\\\"apiKey\\\":\\\"" + TEXT_REDACTED + "\\\",\\\"safe\\\":\\\"keep\\\"}");

        // Adversarial unclosed escaped JSON value must finish in linear time.
        String adversarial = "{\\\"password\\\":\\\"" + "\\".repeat(1000);
        long startNanos = System.nanoTime();
        String redacted = SensitiveDataRedactor.redactText(adversarial);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(1_000L);
        assertThat(redacted).contains(TEXT_REDACTED);
    }
}
