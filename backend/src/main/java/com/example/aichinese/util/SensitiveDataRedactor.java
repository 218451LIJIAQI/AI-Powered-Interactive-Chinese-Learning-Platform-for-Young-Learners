package com.example.aichinese.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central redaction rules for log and audit text.
 *
 * <p>Keep this class conservative: leaking one token, email address, or
 * session identifier in a log stream is much worse than over-redacting a
 * diagnostic sentence.</p>
 */
public final class SensitiveDataRedactor {

    public static final String REDACTED = "[REDACTED]";

    /**
     * Control characters other than line breaks and tabs, and the bidirectional embedding, override and isolate
     * controls (U+202A-U+202E, U+2066-U+2069).
     */
    private static final Pattern UNSAFE_LOG_CHARACTERS =
            Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]|[\\u202A-\\u202E\\u2066-\\u2069]");
    private static final String SENSITIVE_KEYS =
            "(?:(?:access|refresh|id|reset|csrf|session|invite|confirm(?:ation)?)[-_ ]?)?(?:token|jwt)"
                    + "|(?:session[-_ ]?id|jsessionid|sid)"
                    + "|(?:password|token)[-_ ]?hash"
                    + "|(?:invite|confirm(?:ation)?)[-_ ]?code|invite"
                    + "|(?:(?:current|new|confirm|old)[-_ ]?)?password|passcode|credential"
                    + "|(?:(?:client|app|jwt|signing)[-_ ]?)?secret(?:[-_ ]?(?:key|id))?"
                    + "|(?:x[-_ ]?)?api[-_ ]?key|email";
    private static final String SENSITIVE_LINK_KEYS = "(?:reset|invite|confirm(?:ation)?)[-_ ]?link";
    private static final String JSON_SENSITIVE_KEYS =
            SENSITIVE_KEYS + "|" + SENSITIVE_LINK_KEYS + "|authorization|(?:set[-_ ]?)?cookie";
    private static final Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile(
            "(?i)(\\bauthorization\\s*(?:[:=])\\s*)(?:" + quotedValue(2)
                    + "|\\[[^\\r\\n\\]]*\\]"
                    + "|(?:digest|aws4-hmac-sha256|tc3-hmac-sha256)\\s+[^\\r\\n]+"
                    + "|(?:(?:bearer|basic|token|apikey|api[-_]?key|negotiate)\\s+)?\\S+)");
    private static final Pattern COOKIE_HEADER_PATTERN = Pattern.compile(
            "(?i)(\\b(?:set[-_ ]?)?cookie\\s*(?:[:=])\\s*)(?:" + quotedValue(2) + "[^\\r\\n]*|[^\\r\\n]+)");
    /** "Bearer &lt;token&gt;"; the words "bearer token(s)" in a sentence are not a credential. */
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\bbearer\\s+(?!tokens?\\b)[^\\s,;\\]]+");
    /**
     * Quoted JSON properties whose key names a secret or credential; backreferences the opening quote so apostrophes
     * inside double-quoted values (e.g. {@code "don't-leak"}) cannot terminate redaction early.
     */
    private static final Pattern JSON_SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)([\"'](?:" + JSON_SENSITIVE_KEYS + ")[\"']\\s*:\\s*([\"']))" + quotedBody(2));
    private static final Pattern JSON_SENSITIVE_NUMERIC_PATTERN = Pattern.compile(
            "(?i)([\"'](?:" + JSON_SENSITIVE_KEYS + ")[\"']\\s*:\\s*)-?\\d+(?:\\.\\d+)?");
    /**
     * JSON-inside-JSON escaped properties (e.g. {@code {\"password\":\"abc\\\"def\"}}): consumes double-escaped
     * backslashes ({@code \\\\}) and inner escaped quotes ({@code \\\"}) possessively so inner quotes never leak
     * trailing secret characters and unclosed strings finish in O(N) time.
     */
    private static final Pattern ESCAPED_JSON_SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\\\\"(?:" + JSON_SENSITIVE_KEYS + ")\\\\\"\\s*:\\s*\\\\\")"
                    + "(?:\\\\\\\\\\\\\\\\|\\\\\\\\\\\\\"|\\\\(?!\")|[^\\\\])*+(?:\\\\\"|\\\\?\\z)");
    private static final Pattern RESET_LINK_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(" + SENSITIVE_LINK_KEYS + ")\\b\\s*(?:[:=])\\s*(?:" + quotedValue(2) + "|\\S+)");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(" + SENSITIVE_KEYS + ")\\b\\s*(?:[:=])\\s*(?:" + quotedValue(2) + "|[^\\s\"',;&}]+)");

    private SensitiveDataRedactor() {
    }

    /** Opening quote capture + disjoint, possessive body so unclosed quotes with backslashes cannot backtrack exponentially. */
    private static String quotedValue(int quoteGroup) {
        return "([\"'])" + quotedBody(quoteGroup);
    }

    private static String quotedBody(int quoteGroup) {
        return "(?:\\\\[\\s\\S]|(?!\\" + quoteGroup + ")[^\\\\])*+(?:\\" + quoteGroup + "|\\\\?\\z)";
    }

    public static String redactText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String redacted = UNSAFE_LOG_CHARACTERS.matcher(value).replaceAll(" ");
        redacted = JSON_SENSITIVE_VALUE_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + REDACTED + match.group(2)));
        redacted = JSON_SENSITIVE_NUMERIC_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "\"" + REDACTED + "\""));
        redacted = ESCAPED_JSON_SENSITIVE_VALUE_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + REDACTED + "\\\""));
        redacted = AUTHORIZATION_HEADER_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + REDACTED));
        redacted = COOKIE_HEADER_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + REDACTED));
        redacted = BEARER_TOKEN_PATTERN.matcher(redacted)
                .replaceAll("Bearer " + REDACTED);
        redacted = RESET_LINK_ASSIGNMENT_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "=" + REDACTED));
        redacted = SENSITIVE_ASSIGNMENT_PATTERN.matcher(redacted)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "=" + REDACTED));
        return PersonalDataRedactor.EMAIL_PATTERN.matcher(redacted).replaceAll(REDACTED);
    }
}
