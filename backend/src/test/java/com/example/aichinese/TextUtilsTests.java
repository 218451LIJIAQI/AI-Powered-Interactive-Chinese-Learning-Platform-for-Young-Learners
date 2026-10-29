package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.aichinese.util.TextUtils;

/**
 * Unit tests for {@link TextUtils}. These pin down the exact behaviour of the
 * shared text helpers so that future refactors can rely on stable semantics.
 */
class TextUtilsTests {

    private static final String FALLBACK = "fallback";
    private static final String IDEOGRAPHIC_SPACE = "　";
    private static final String EMOJI = "😀";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   \t\n  ")
    void trimToNullReturnsNullForBlankInputs(String blank) {
        assertThat(TextUtils.trimToNull(blank)).isNull();
    }

    /** Surrounding whitespace is trimmed; internal whitespace is kept. */
    @ParameterizedTest
    @CsvSource({"'  hello  ', hello", "'  hello world  ', hello world"})
    void trimToNullReturnsTrimmedValueWhenContentExists(String value, String expected) {
        assertThat(TextUtils.trimToNull(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void trimToOptionalReturnsEmptyForBlankInputs(String blank) {
        assertThat(TextUtils.trimToOptional(blank)).isEmpty();
    }

    @Test
    void trimToOptionalReturnsTrimmedValue() {
        assertThat(TextUtils.trimToOptional("  abc  ")).hasValue("abc");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "   ")
    void defaultIfBlankReturnsFallbackForBlankInputs(String blank) {
        assertThat(TextUtils.defaultIfBlank(blank, FALLBACK)).isEqualTo(FALLBACK);
    }

    /** Important contract: defaultIfBlank keeps the caller's exact whitespace (AiService relied on it for prompts). */
    @ParameterizedTest
    @ValueSource(strings = {"  abc  ", "abc"})
    void defaultIfBlankReturnsNonBlankValueAsIsWithoutTrimming(String value) {
        assertThat(TextUtils.defaultIfBlank(value, FALLBACK)).isEqualTo(value);
    }

    @Test
    void collapseWhitespaceToNullHandlesUnicodeWhitespaceAndNewlines() {
        assertThat(TextUtils.collapseWhitespaceToNull(IDEOGRAPHIC_SPACE + "Hello\n\tworld" + IDEOGRAPHIC_SPACE))
                .isEqualTo("Hello world");
        assertThat(TextUtils.collapseWhitespaceToNull(IDEOGRAPHIC_SPACE + "\t\n")).isNull();
    }

    @Test
    void safeTruncateDoesNotSplitSurrogatePair() {
        assertThat(TextUtils.safeTruncate("ab" + EMOJI + "cd", 3)).isEqualTo("ab");
        assertThat(TextUtils.safeTruncate("ab" + EMOJI + "cd", 4)).isEqualTo("ab" + EMOJI);
    }

    @Test
    void truncateWithEllipsisCollapsesWhitespaceAndKeepsLengthCap() {
        assertThat(TextUtils.truncateWithEllipsis("  Hello\nworld " + EMOJI + " again  ", 15))
                .isEqualTo("Hello world...");
    }
}
