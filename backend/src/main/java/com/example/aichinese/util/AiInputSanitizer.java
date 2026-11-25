package com.example.aichinese.util;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.web.server.ResponseStatusException;

/**
 * Cleans plain text used in AI prompts, AI responses and stored free-text fields. Removes known chat-template tokens
 * and invisible controls, keeps internal tabs and line breaks, and caps the result in UTF-16 units at a code point boundary.
 */
public final class AiInputSanitizer {

    /** The cap for a short label: a topic, a level, an answer option. */
    public static final int DEFAULT_SHORT_INPUT_LIMIT = 200;

    /** The cap for free-form text: a chat message, a chat-history turn, a field of an AI reply. */
    public static final int DEFAULT_LONG_INPUT_LIMIT = 2000;

    /** Known fixed chat-template tokens (matched case-insensitively). All end with {@code '>'} or {@code ']'}. */
    private static final List<String> FIXED_CHAT_CONTROL_TOKENS = List.of(
            // ChatML / OpenAI-compatible
            "<|im_start|>",
            "<|im_end|>",
            "<|im_sep|>",
            "<|endoftext|>",
            "<|fim_prefix|>",
            "<|fim_middle|>",
            "<|fim_suffix|>",
            "<|system|>",
            "<|user|>",
            "<|assistant|>",
            // Llama 3 special tokens
            "<|begin_of_text|>",
            "<|end_of_text|>",
            "<|start_header_id|>",
            "<|end_header_id|>",
            "<|eot_id|>",
            "<|eom_id|>",
            "<|finetune_right_pad_id|>",
            // Gemma
            "<start_of_turn>",
            "<end_of_turn>",
            // Llama 2 instruction wrappers
            "[INST]",
            "[/INST]",
            "<<SYS>>",
            "<</SYS>>");
    private static final String RESERVED_TOKEN_PREFIX = "<|reserved_special_token_";
    private static final String RESERVED_TOKEN_SUFFIX = "|>";

    private AiInputSanitizer() {
    }

    /**
     * Sanitise text with an explicit length cap.
     *
     * @param input     raw text; may be {@code null}.
     * @param maxLength inclusive maximum length of the returned string in
     *                  Java {@code char} units. Must be positive.
     * @return the sanitised text, or {@code null} if the input is
     *         {@code null}, blank-only or empty after sanitising.
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive.");
        }
        String text = cleanText(input);
        String limited = TextUtils.stripFormSpaces(TextUtils.safeTruncate(text, maxLength));
        return limited.isEmpty() ? null : limited;
    }

    /** Sanitise text without applying a length cap, returning {@code null} when nothing remains. */
    public static String sanitizeUnbounded(String input) {
        if (input == null) {
            return null;
        }
        String text = cleanText(input);
        return text.isEmpty() ? null : text;
    }

    private static String cleanText(String input) {
        // Characters are removed before tokens so that a control or invisible character placed
        // inside a sentinel (for example a U+200B zero-width space or U+00AD soft hyphen) cannot hide it.
        String clean = stripUnsafeCharacters(input);
        return TextUtils.stripFormSpaces(stripChatControlTokens(clean));
    }

    /** Strips ISO control characters (except tab and line breaks) and Unicode format characters (Cf: bidi, ZW*, tags). */
    private static String stripUnsafeCharacters(String input) {
        StringBuilder out = null;
        for (int index = 0; index < input.length();) {
            int codePoint = input.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (isUnsafeCodePoint(codePoint)) {
                if (out == null) {
                    out = new StringBuilder(input.length());
                    out.append(input, 0, index);
                }
            } else if (out != null) {
                out.appendCodePoint(codePoint);
            }
            index += charCount;
        }
        return out == null ? input : out.toString();
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
            return false;
        }
        return Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT;
    }

    /**
     * Removes chat-template tokens in a single linear left-to-right pass so nested fragments (such as
     * {@code "<|im_<|im_end|>start|>"}) collapse as each closing {@code '>'} or {@code ']'} arrives without O(N^2) rescanning.
     */
    private static String stripChatControlTokens(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            out.append(ch);
            if (ch == '>' || ch == ']') {
                int tokenLength = trailingChatControlTokenLength(out);
                if (tokenLength > 0) {
                    out.setLength(out.length() - tokenLength);
                }
            }
        }
        return out.toString();
    }

    private static int trailingChatControlTokenLength(StringBuilder out) {
        int length = out.length();
        for (String token : FIXED_CHAT_CONTROL_TOKENS) {
            int tokenLen = token.length();
            if (length >= tokenLen && matchesIgnoreCase(out, length - tokenLen, token)) {
                return tokenLen;
            }
        }
        // Llama 3 numbered token: <|reserved_special_token_\d+|>
        int suffixLen = RESERVED_TOKEN_SUFFIX.length();
        int prefixLen = RESERVED_TOKEN_PREFIX.length();
        if (length >= prefixLen + 1 + suffixLen && matchesIgnoreCase(out, length - suffixLen, RESERVED_TOKEN_SUFFIX)) {
            int digitEnd = length - suffixLen;
            int digitStart = digitEnd;
            while (digitStart > prefixLen && isAsciiDigit(out.charAt(digitStart - 1))) {
                digitStart--;
            }
            if (digitStart < digitEnd && matchesIgnoreCase(out, digitStart - prefixLen, RESERVED_TOKEN_PREFIX)) {
                return length - (digitStart - prefixLen);
            }
        }
        return 0;
    }

    private static boolean matchesIgnoreCase(StringBuilder text, int offset, String asciiToken) {
        for (int i = 0; i < asciiToken.length(); i++) {
            char actual = text.charAt(offset + i);
            char expected = asciiToken.charAt(i);
            if (actual != expected && toAsciiLower(actual) != toAsciiLower(expected)) {
                return false;
            }
        }
        return true;
    }

    private static char toAsciiLower(char ch) {
        return ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch;
    }

    private static boolean isAsciiDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /** {@link #sanitize} capped at {@link #DEFAULT_SHORT_INPUT_LIMIT}. */
    public static String sanitizeShort(String input) {
        return sanitize(input, DEFAULT_SHORT_INPUT_LIMIT);
    }

    /** {@link #sanitize} capped at {@link #DEFAULT_LONG_INPUT_LIMIT}. */
    public static String sanitizeLong(String input) {
        return sanitize(input, DEFAULT_LONG_INPUT_LIMIT);
    }

    /** Sanitises text at {@link #DEFAULT_SHORT_INPUT_LIMIT}; 400 with {@code message} when blank after cleaning. */
    public static String requireShort(String value, String message) {
        return requireText(value, DEFAULT_SHORT_INPUT_LIMIT, message);
    }

    /** Sanitises text at {@link #DEFAULT_LONG_INPUT_LIMIT}; 400 with {@code message} when blank after cleaning. */
    public static String requireLong(String value, String message) {
        return requireText(value, DEFAULT_LONG_INPUT_LIMIT, message);
    }

    /** Sanitises text with an explicit length cap; 400 with {@code message} when nothing is left of it. */
    public static String requireText(String value, int maxLength, String message) {
        String sanitized = sanitize(value, maxLength);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }
}
