package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.TextUtils;

/**
 * Unit tests for {@link TextUtils}. These pin down the exact behaviour of the
 * shared text helpers so that future refactors can rely on stable semantics —
 * the same semantics that the previously duplicated copies depended on.
 */
class TextUtilsTests {

    @Test
    void trimToNullReturnsNullForNullInput() {
        assertThat(TextUtils.trimToNull(null)).isNull();
    }

    @Test
    void trimToNullReturnsNullForEmptyString() {
        assertThat(TextUtils.trimToNull("")).isNull();
    }

    @Test
    void trimToNullReturnsNullForWhitespaceOnly() {
        assertThat(TextUtils.trimToNull("   \t\n  ")).isNull();
    }

    @Test
    void trimToNullReturnsTrimmedValueWhenContentExists() {
        assertThat(TextUtils.trimToNull("  hello  ")).isEqualTo("hello");
    }

    @Test
    void trimToNullPreservesInternalWhitespace() {
        assertThat(TextUtils.trimToNull("  hello world  ")).isEqualTo("hello world");
    }

    @Test
    void trimToOptionalReturnsEmptyForBlankInputs() {
        assertThat(TextUtils.trimToOptional(null)).isEmpty();
        assertThat(TextUtils.trimToOptional("")).isEmpty();
        assertThat(TextUtils.trimToOptional("   ")).isEmpty();
    }

    @Test
    void trimToOptionalReturnsTrimmedValue() {
        Optional<String> result = TextUtils.trimToOptional("  abc  ");
        assertThat(result).hasValue("abc");
    }

    @Test
    void defaultIfBlankReturnsFallbackForNullInput() {
        assertThat(TextUtils.defaultIfBlank(null, "fallback")).isEqualTo("fallback");
    }

    @Test
    void defaultIfBlankReturnsFallbackForBlankInput() {
        assertThat(TextUtils.defaultIfBlank("   ", "fallback")).isEqualTo("fallback");
    }

    @Test
    void defaultIfBlankReturnsValueAsIsWithoutTrimming() {
        // Important contract: defaultIfBlank preserves the caller's exact
        // whitespace. AiService relied on this when building prompts.
        assertThat(TextUtils.defaultIfBlank("  abc  ", "fallback")).isEqualTo("  abc  ");
    }

    @Test
    void defaultIfBlankReturnsValueWhenNonBlank() {
        assertThat(TextUtils.defaultIfBlank("abc", "fallback")).isEqualTo("abc");
    }
}
