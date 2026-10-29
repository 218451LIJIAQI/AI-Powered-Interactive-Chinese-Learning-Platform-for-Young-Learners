package com.example.aichinese.util;

import java.util.List;

/**
 * Builds RFC 4180 CSV text. Cells are quoted when needed, and a cell that a spreadsheet would read as a formula
 * (starting with =, +, -, @, tab or carriage return) is prefixed with an apostrophe.
 */
public final class CsvWriter {

    private static final String LINE_END = "\r\n";

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

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (!raw.isEmpty() && "=+-@\t\r".indexOf(raw.charAt(0)) >= 0) {
            raw = "'" + raw;
        }
        boolean needsQuotes = raw.indexOf(',') >= 0 || raw.indexOf('"') >= 0
                || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
        return needsQuotes ? '"' + raw.replace("\"", "\"\"") + '"' : raw;
    }
}
