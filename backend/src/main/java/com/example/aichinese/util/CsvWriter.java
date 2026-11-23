package com.example.aichinese.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds RFC 4180 CSV text. Cells are quoted when needed, and a cell that a spreadsheet would read as a formula
 * (starting with {@code =}, {@code +}, {@code -}, {@code @}, tab, CR or LF, or leading spaces followed by a formula
 * trigger) is prefixed with an apostrophe. Local times are written as {@code yyyy-MM-dd HH:mm:ss} and offset times as
 * {@code yyyy-MM-dd HH:mm:ssXXX}.
 */
public final class CsvWriter {

    private static final String LINE_END = "\r\n";
    private static final String FORMULA_PREFIXES = "=+-@";
    private static final String FORMULA_LEADING_TRIGGERS = "=+-@\t\r\n";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter OFFSET_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX", Locale.ROOT);

    private final StringBuilder text = new StringBuilder();

    public CsvWriter row(List<?> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(cell(cells.get(index)));
        }
        text.append(LINE_END);
        return this;
    }

    @Override
    public String toString() {
        return text.toString();
    }

    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String raw = switch (value) {
            case OffsetDateTime offsetTime -> OFFSET_TIME.format(offsetTime);
            case LocalDateTime localTime -> TIME.format(localTime);
            default -> value.toString();
        };
        if (needsFormulaNeutralization(raw)) {
            raw = "'" + raw;
        }
        boolean needsQuotes = raw.indexOf(',') >= 0 || raw.indexOf('"') >= 0
                || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
        return needsQuotes ? '"' + raw.replace("\"", "\"\"") + '"' : raw;
    }

    private static boolean needsFormulaNeutralization(String raw) {
        if (raw.isEmpty()) {
            return false;
        }
        if (FORMULA_LEADING_TRIGGERS.indexOf(raw.charAt(0)) >= 0) {
            return true;
        }
        int offset = 0;
        while (offset < raw.length()) {
            int codePoint = raw.codePointAt(offset);
            if (!TextUtils.isSpace(codePoint)) {
                return codePoint < 128 && FORMULA_PREFIXES.indexOf(codePoint) >= 0;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}
