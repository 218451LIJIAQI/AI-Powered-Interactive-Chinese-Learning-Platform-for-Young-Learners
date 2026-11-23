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
    private static final String IDEOGRAPHIC_SPACE = "\u3000";
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
    @NullSource
    @ValueSource(strings = "   ")
    void defaultIfBlankReturnsFallbackForBlankInputs(String blank) {
        assertThat(TextUtils.defaultIfBlank(blank, FALLBACK)).isEqualTo(FALLBACK);
    }

    /** Important contract: defaultIfBlank keeps the caller's exact whitespace. */
    @ParameterizedTest
    @ValueSource(strings = {"  abc  ", "abc"})
    void defaultIfBlankReturnsNonBlankValueAsIsWithoutTrimming(String value) {
        assertThat(TextUtils.defaultIfBlank(value, FALLBACK)).isEqualTo(value);
    }

    /** What the browser's trim() removes is blank: no-break spaces (U+00A0, U+2007, U+202F) and U+FEFF too. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n", "\u00A0", "\u2007\u202F", " \u00A0\u3000 ", "\uFEFF", " \uFEFF\u00A0 "})
    void isBlankOrSpacesCountsEverySpaceSeparatorAsBlank(String blank) {
        assertThat(TextUtils.isBlankOrSpaces(blank)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"你好", "\u00A0好\u00A0", "a", "\uFEFF好"})
    void isBlankOrSpacesKeepsAnyOtherCharacter(String text) {
        assertThat(TextUtils.isBlankOrSpaces(text)).isFalse();
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
        assertThat(TextUtils.safeTruncate(EMOJI, 0)).isEmpty();
    }

    @Test
    void truncateWithEllipsisCollapsesWhitespaceAndKeepsLengthCap() {
        assertThat(TextUtils.truncateWithEllipsis("  Hello\nworld " + EMOJI + " again  ", 15))
                .isEqualTo("Hello world...");
        assertThat(TextUtils.truncateWithEllipsis("Hello", 2)).isEqualTo("..");
        assertThat(TextUtils.truncateWithEllipsis("Hello", 0)).isEmpty();
        assertThat(TextUtils.truncateWithEllipsis(null, 10)).isNull();
        assertThat(TextUtils.truncateWithEllipsis("\u00A0\uFEFF  ", 10)).isNull();
    }

    @Test
    void whitespaceHelpersShareBrowserCompatibleSpaceDefinition() {
        assertThat(TextUtils.trimToNull("\u00A0\uFEFF\u3000")).isNull();
        assertThat(TextUtils.trimToNull("\u00A0hello\u202F")).isEqualTo("hello");
        assertThat(TextUtils.lowerCaseOrNull("\u00A0Hello WORLD\uFEFF")).isEqualTo("hello world");
        assertThat(TextUtils.lowerCaseOrNull("\u00A0\uFEFF")).isNull();
        assertThat(TextUtils.defaultIfBlank("\u00A0\uFEFF", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(TextUtils.stripFormSpaces(null)).isEmpty();
        assertThat(TextUtils.stripFormSpaces("\uFEFF\u00A0a b\u2007\u202F\u3000")).isEqualTo("a b");
        assertThat(TextUtils.collapseWhitespaceToNull("\u00A0hello\u00A0\uFEFFworld\u3000")).isEqualTo("hello world");
        assertThat(TextUtils.safeTruncate(null, 5)).isNull();
        // Browser trim() does not treat U+0085 (NEL) as blank, whereas one-line collapsing strips all Unicode line breaks.
        assertThat(TextUtils.isBlankOrSpaces("\u0085")).isFalse();
        assertThat(TextUtils.trimToNull("\u0085")).isEqualTo("\u0085");
        assertThat(TextUtils.collapseWhitespaceToNull("a\u0085b")).isEqualTo("a b");
        assertThat(TextUtils.collapseWhitespaceToNull("\u0085")).isNull();
        assertThat(TextUtils.truncateWithEllipsis("\u0085", 80)).isNull();
    }

    @Test
    void normalizeAnswerTextFoldsWidthAndSpacesWhilePreservingPinyinTones() {
        assertThat(TextUtils.normalizeAnswerText(null)).isEmpty();
        assertThat(TextUtils.normalizeAnswerText("  Ｎǐ\u00A0Hǎo\uFEFF  ")).isEqualTo("nǐhǎo");
        assertThat(TextUtils.normalizeAnswerText("nǐ hǎo"))
                .isNotEqualTo(TextUtils.normalizeAnswerText("ní hǎo"));
    }
}
