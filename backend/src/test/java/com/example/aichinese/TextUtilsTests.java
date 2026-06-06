package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.TextUtils;

/**
 * Unit tests for {@link TextUtils}. These pin down the exact behaviour of the
 * shared text helpers so that future refactors can rely on stable semantics -
 * the same semantics that the previously duplicated copies depended on.
 */
class TextUtilsTests {

    private static final String FALLBACK = "fallback";
    private static final String ABC = "abc";
    private static final String PADDED_ABC = "  abc  ";
    private static final String PADDED_HELLO = "  hello  ";
    private static final String HELLO = "hello";
    private static final String PADDED_HELLO_WORLD = "  hello world  ";
    private static final String HELLO_WORLD = "hello world";
    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String EMOJI = "😀";

    @Test
    void trimToNullReturnsNullForBlankInputs() {
        assertTrimToNull(null, null);
        assertTrimToNull("", null);
        assertTrimToNull("   \t\n  ", null);
    }

    @Test
    void trimToNullReturnsTrimmedValueWhenContentExists() {
        assertTrimToNull(PADDED_HELLO, HELLO);
    }

    @Test
    void trimToNullPreservesInternalWhitespace() {
        assertTrimToNull(PADDED_HELLO_WORLD, HELLO_WORLD);
    }

    @Test
    void trimToOptionalReturnsEmptyForBlankInputs() {
        assertTrimToOptionalIsEmpty(null);
        assertTrimToOptionalIsEmpty("");
        assertTrimToOptionalIsEmpty("   ");
    }

    @Test
    void trimToOptionalReturnsTrimmedValue() {
        assertThat(TextUtils.trimToOptional(PADDED_ABC)).hasValue(ABC);
    }

    @Test
    void defaultIfBlankReturnsFallbackForBlankInputs() {
        assertDefaultIfBlank(null, FALLBACK);
        assertDefaultIfBlank("   ", FALLBACK);
    }

    @Test
    void defaultIfBlankReturnsValueAsIsWithoutTrimming() {
        // Important contract: defaultIfBlank preserves the caller's exact
        // whitespace. AiService relied on this when building prompts.
        assertDefaultIfBlank(PADDED_ABC, PADDED_ABC);
    }

    @Test
    void defaultIfBlankReturnsValueWhenNonBlank() {
        assertDefaultIfBlank(ABC, ABC);
    }

    @Test
    void collapseWhitespaceToNullHandlesUnicodeWhitespaceAndNewlines() {
        assertThat(TextUtils.collapseWhitespaceToNull(
                IDEOGRAPHIC_SPACE + "Hello\n\tworld" + IDEOGRAPHIC_SPACE))
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
        String result = TextUtils.truncateWithEllipsis("  Hello\nworld " + EMOJI + " again  ", 15);

        assertThat(result).isEqualTo("Hello world...");
        assertThat(result).hasSizeLessThanOrEqualTo(15);
    }

    private static void assertTrimToNull(String value, String expected) {
        assertThat(TextUtils.trimToNull(value)).isEqualTo(expected);
    }

    private static void assertDefaultIfBlank(String value, String expected) {
        assertThat(TextUtils.defaultIfBlank(value, FALLBACK)).isEqualTo(expected);
    }

    private static void assertTrimToOptionalIsEmpty(String value) {
        assertThat(TextUtils.trimToOptional(value)).isEmpty();
    }
}
