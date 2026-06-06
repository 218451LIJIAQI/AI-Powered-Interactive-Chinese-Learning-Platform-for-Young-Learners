package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.ExceptionLogUtils;

class ExceptionLogUtilsTests {

    private static final int MAX_SAFE_SUMMARY_LENGTH = 220;

    @Test
    void safeSummaryRedactsSensitiveValuesAndCollapsesLayoutControl() {
        RuntimeException exception = new RuntimeException(sensitiveMessage());

        String summary = ExceptionLogUtils.safeSummary(exception);

        assertThat(summary)
                .startsWith("RuntimeException:")
                .contains("password=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .contains("token=[REDACTED]")
                .doesNotContain("secret123")
                .doesNotContain("abc.def")
                .doesNotContain("reset-token")
                .doesNotContain("\n")
                .doesNotContain("\u202E")
                .hasSizeLessThanOrEqualTo(MAX_SAFE_SUMMARY_LENGTH);
    }

    @Test
    void safeSummaryFallsBackToExceptionTypeWhenMessageIsBlank() {
        assertThat(ExceptionLogUtils.safeSummary(new IllegalStateException("  ")))
                .isEqualTo("IllegalStateException");
        assertThat(ExceptionLogUtils.safeSummary(null))
                .isEqualTo("UnknownException");
    }

    private static String sensitiveMessage() {
        return "password=secret123\nAuthorization: Bearer abc.def token:reset-token \u202E" + "x".repeat(300);
    }
}
