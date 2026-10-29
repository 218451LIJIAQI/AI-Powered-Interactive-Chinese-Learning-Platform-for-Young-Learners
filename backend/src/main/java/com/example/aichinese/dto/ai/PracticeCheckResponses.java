package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

/** Pronunciation and handwriting practice checks: only the student who made them sees them. */
public final class PracticeCheckResponses {

    private PracticeCheckResponses() {
    }

    /**
     * One scored check. {@code tonesScore}, {@code clarityScore} and {@code paceScore} are set for recordings only;
     * {@code feedback} is the general comment (the stroke feedback for handwriting) and {@code tips} what to work on.
     */
    public record Check(
            Long id,
            String type,
            String targetText,
            String lessonTitle,
            int score,
            Integer tonesScore,
            Integer clarityScore,
            Integer paceScore,
            String headline,
            String feedback,
            List<String> tips,
            LocalDateTime checkedAt) {

        public Check {
            tips = tips == null ? List.of() : List.copyOf(tips);
        }
    }

    /** The outcome of "Check with SparkBot": the saved check on SUCCESS, otherwise the fixed message that says why. */
    public record Result(String status, String message, Check check) {
    }

    /** A lesson the student can link a check to. */
    public record Lesson(Long id, String title) {
    }
}
