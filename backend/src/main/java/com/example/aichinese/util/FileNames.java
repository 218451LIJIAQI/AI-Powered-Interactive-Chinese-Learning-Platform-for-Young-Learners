package com.example.aichinese.util;

/**
 * File-name cleaning shared by the upload pipeline (the stored original name) and the download response
 * ({@code Content-Disposition} file name).
 */
public final class FileNames {

    private FileNames() {
    }

    /**
     * Replaces control, format and path/shell-tricky characters ({@code " * / : < > ? \ |}) with a single
     * {@code _} per run, collapses whitespace runs to one space, and drops leading dots and surrounding
     * whitespace. The result may be empty.
     */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWasWhitespace = false;

        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isUnsafeCodePoint(codePoint)) {
                appendReplacement(sanitized);
                previousWasWhitespace = false;
                continue;
            }

            if (TextUtils.isSpace(codePoint)) {
                if (!previousWasWhitespace && !sanitized.isEmpty()) {
                    sanitized.append(' ');
                    previousWasWhitespace = true;
                }
                continue;
            }

            sanitized.appendCodePoint(codePoint);
            previousWasWhitespace = false;
        }

        // Drop leading dots/spaces and any trailing space left before the end of the name.
        int start = 0;
        while (start < sanitized.length() && (sanitized.charAt(start) == '.' || sanitized.charAt(start) == ' ')) {
            start++;
        }
        int end = sanitized.length();
        while (end > start && sanitized.charAt(end - 1) == ' ') {
            end--;
        }
        return sanitized.substring(start, end);
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || switch (codePoint) {
                    case '"', '*', '/', ':', '<', '>', '?', '\\', '|' -> true;
                    default -> false;
                };
    }

    private static void appendReplacement(StringBuilder sanitized) {
        if (sanitized.isEmpty() || sanitized.charAt(sanitized.length() - 1) != '_') {
            sanitized.append('_');
        }
    }
}
