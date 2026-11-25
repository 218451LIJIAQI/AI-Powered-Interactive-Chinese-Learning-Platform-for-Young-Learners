package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.value.PronunciationFeedback;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pronunciation and handwriting practice checks: only the student who made them sees them. */
public final class PracticeCheckResponses {

    private PracticeCheckResponses() {
    }

    /**
     * One scored check. New recordings have {@code pronunciation}; the old sub-score fields read historical records.
     * {@code feedback} is the written comment and {@code tips} what to work on.
     */
    public record Check(
            Long id,
            PracticeCheckType type,
            String targetText,
            String lessonTitle,
            int score,
            Integer clarityScore,
            Integer paceScore,
            PronunciationFeedback pronunciation,
            String headline,
            String feedback,
            List<String> tips,
            LocalDateTime checkedAt) {

        @Override
        public String toString() {
            return "Check[id=" + id
                    + ", type=" + type
                    + ", targetText=" + redactLength(targetText)
                    + ", score=" + score
                    + ", headline=" + redactLength(headline)
                    + ", feedback=" + redactLength(feedback)
                    + ", tips=" + (tips == null ? 0 : tips.size())
                    + ", checkedAt=" + checkedAt + "]";
        }
    }

    /** The outcome of "Check with SparkBot": the saved check on SUCCESS, otherwise the fixed message that says why. */
    @Schema(name = "PracticeCheckResult")
    public record Result(AiRequestStatus status, String message, Check check) {
    }

    /** A lesson the student can link a check to, with its key phrases to practise. */
    public record Lesson(Long id, String title, List<Phrase> phrases) {
    }

    /** A key phrase of a lesson. */
    @Schema(name = "PracticeCheckPhrase")
    public record Phrase(String chinese) {
    }
}
