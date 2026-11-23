package com.example.aichinese.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helpers for normalising user-supplied text. All blankness and stripping rules share {@link #isSpace(int)} (matching
 * browser {@code trim()}): Java whitespace, Unicode space separators (including {@code U+00A0}, {@code U+2007},
 * {@code U+202F} and ideographic {@code U+3000}), and {@code U+FEFF}.
 */
public final class TextUtils {

    private static final String ELLIPSIS = "...";

    /**
     * All Unicode whitespace (including {@code U+0085} NEL, {@code U+2028}/{@code U+2029} line separators, space
     * separators) plus Java whitespace and {@code U+FEFF}, so answer normalisation and one-line summaries never retain
     * line breaks or invisible spacing.
     */
    private static final Pattern UNICODE_SPACES = Pattern.compile("(?U)[\\s\\p{javaWhitespace}\\x{FEFF}]+");

    /** U+FEFF, the byte-order mark: not a space for Java, but the browser's {@code trim()} removes it. */
    private static final int ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;

    private TextUtils() {
    }

    /** Quiz, matching and Learn answer keys ignore case, width and spacing. Pinyin tones remain significant. */
    public static String normalizeAnswerText(String value) {
        if (value == null) {
            return "";
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return UNICODE_SPACES.matcher(folded).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** The stripped value, or {@code null} when it is null or blank. */
    public static String trimToNull(String value) {
        String stripped = stripFormSpaces(value);
        return stripped.isEmpty() ? null : stripped;
    }

    /** The stripped value in lower case (a search keyword or filter), or {@code null} when it is null or blank. */
    public static String lowerCaseOrNull(String value) {
        String stripped = trimToNull(value);
        return stripped == null ? null : stripped.toLowerCase(Locale.ROOT);
    }

    /** {@code value} unchanged (not stripped) unless it is null or blank, then {@code fallback}. */
    public static String defaultIfBlank(String value, String fallback) {
        return isBlankOrSpaces(value) ? fallback : value;
    }

    /**
     * True when {@code value} is null or holds only what the browser's {@code trim()} removes: whitespace, Unicode
     * space separators and U+FEFF. Unlike {@link String#isBlank()}, the no-break spaces (U+00A0, U+2007, U+202F) and
     * U+FEFF count as blank too.
     */
    public static boolean isBlankOrSpaces(String value) {
        return value == null || value.codePoints().allMatch(TextUtils::isSpace);
    }

    /** Whitespace recognized when testing form input for blankness. */
    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == ZERO_WIDTH_NO_BREAK_SPACE;
    }

    /** A form label without surrounding spaces, including non-breaking spaces. A missing label is empty. */
    public static String stripFormSpaces(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * One line: every whitespace run becomes one space and the result is stripped; {@code null} when no text remains.
     * For summaries where multi-line input must not carry its layout into a log or a response.
     */
    public static String collapseWhitespaceToNull(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = stripFormSpaces(UNICODE_SPACES.matcher(value).replaceAll(" "));
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** At most {@code maxLength} UTF-16 code units, never splitting a surrogate pair; {@code null} stays {@code null}. */
    public static String safeTruncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 0) {
            return "";
        }
        boolean splitsPair = Character.isSurrogatePair(value.charAt(maxLength - 1), value.charAt(maxLength));
        return value.substring(0, splitsPair ? maxLength - 1 : maxLength);
    }

    /**
     * A one-line summary (see {@link #collapseWhitespaceToNull}) of at most {@code maxLength} characters, the
     * trailing ellipsis included; {@code null} for null or blank input.
     */
    public static String truncateWithEllipsis(String value, int maxLength) {
        String normalized = collapseWhitespaceToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= ELLIPSIS.length()) {
            return safeTruncate(ELLIPSIS, maxLength);
        }
        return stripFormSpaces(safeTruncate(normalized, maxLength - ELLIPSIS.length())) + ELLIPSIS;
    }
}
