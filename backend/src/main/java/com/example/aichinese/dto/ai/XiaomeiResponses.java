package com.example.aichinese.dto.ai;

import java.util.List;

/**
 * The answers of Talk with Xiaomei. Each carries {@code status} (SUCCESS, FAILED or UNAVAILABLE) and, when it did not
 * succeed, the fixed {@code message} that says why; the AI content is then empty.
 */
public final class XiaomeiResponses {

    private XiaomeiResponses() {
    }

    /** A line of Chinese with its pinyin and its translation into the student's language. */
    public record Line(String chinese, String pinyin, String translation) {
    }

    /**
     * Xiaomei's next line, and a tip when the student's last line could sound more natural: {@code naturalVersion}
     * (the better sentence) and {@code tip} (why), both null when there is nothing to correct.
     */
    public record Reply(String status, String message, Line reply, String naturalVersion, String tip) {
    }

    /** "Help me reply": three short lines the student could say next. */
    public record Suggestions(String status, String message, List<Line> suggestions) {

        public Suggestions {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    /**
     * The end-of-chat summary: Xiaomei's goodbye, short feedback, the useful expressions she used and the words the
     * student used (translation = meaning).
     */
    public record Summary(
            String status,
            String message,
            Line goodbye,
            String feedback,
            List<Line> newExpressions,
            List<Line> wordsUsed) {

        public Summary {
            newExpressions = newExpressions == null ? List.of() : List.copyOf(newExpressions);
            wordsUsed = wordsUsed == null ? List.of() : List.copyOf(wordsUsed);
        }
    }
}
