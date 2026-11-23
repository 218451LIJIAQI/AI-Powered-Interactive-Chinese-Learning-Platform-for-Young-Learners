package com.example.aichinese.entity.value;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A recording's acoustic measurements and the assessed tones aligned with the learner's target. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PronunciationFeedback(
        Integer accuracy, Integer fluency, Integer completeness, List<Issue> issues,
        List<ToneComparison> toneComparisons) {

    public PronunciationFeedback {
        issues = issues == null ? List.of() : List.copyOf(issues);
        // Earlier saved recordings have only scores and issues.
        toneComparisons = toneComparisons == null ? List.of() : List.copyOf(toneComparisons);
    }

    public enum IssueKind { TONE_MISMATCH, OMITTED, MISPRONOUNCED }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(String text, IssueKind kind, Integer expectedTone, Integer detectedTone) {

        public Issue {
            requireText(text, "Issue text");
            Objects.requireNonNull(kind, "Issue kind is required.");
            if (kind == IssueKind.TONE_MISMATCH) {
                if (expectedTone == null || detectedTone == null) {
                    throw new IllegalArgumentException("Tone mismatch issues require expected and detected tones.");
                }
                requireTone(expectedTone, "Expected tone");
                requireTone(detectedTone, "Detected tone");
                if (expectedTone.equals(detectedTone)) {
                    throw new IllegalArgumentException("Tone mismatch expected and detected tones must differ.");
                }
            } else if (expectedTone != null || detectedTone != null) {
                throw new IllegalArgumentException("Non-tone issues must not carry tones.");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToneComparison(String text, int expectedTone, int detectedTone) {

        public ToneComparison {
            requireText(text, "Tone comparison text");
            requireTone(expectedTone, "Expected tone");
            requireTone(detectedTone, "Detected tone");
        }
    }

    private static void requireText(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static void requireTone(int tone, String label) {
        if (tone < 1 || tone > 4) {
            throw new IllegalArgumentException(label + " must be between 1 and 4.");
        }
    }
}
