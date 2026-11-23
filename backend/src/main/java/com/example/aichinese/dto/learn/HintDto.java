package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.HintMode;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Hints in Practice, Learn lessons and the Mistakes review: the student's setting with the rule of their level, a
 * request for an item's hint, and the hint. A hint is one step (A5.05); how much it says follows the level.
 */
public final class HintDto {

    private HintDto() {
    }

    /**
     * What the student's hints look like now.
     *
     * @param tier the rule's level ({@code HSK1}, {@code HSK2} or {@code HSK3}), from the student's plan
     */
    public record Settings(
            HintMode mode,
            String tier,
            int delaySeconds,
            int perSet) {
    }

    public record SettingsUpdate(@NotNull(message = "Choose when hints appear.") HintMode mode) {
    }

    /**
     * The hint for one item; Matching names the word to pair. {@code runStartedAt} and {@code runSignature} are those
     * of the copy of the set being played ({@link PracticeSetResponse}): the set's hint limit counts the items of that
     * run, and a run the server did not start is refused.
     */
    @Schema(name = "PracticeHintRequest")
    public record Request(
            @NotBlank(message = "Item is required.")
            @Size(max = 40, message = "Item is required.")
            String itemKey,
            LocalDateTime runStartedAt,
            @Size(max = 128, message = "Hint run signature is invalid.")
            String runSignature) {

        public Request {
            itemKey = strip(itemKey);
            runSignature = stripToNull(runSignature);
        }
    }

    /**
     * The hint for a Learn question or a waiting mistake. {@code runStartedAt} and {@code runSignature} are those of
     * the lesson ({@link LessonPlayResponse}), or of the review ({@link MistakeReviewResponse}), being played: the
     * level's hints per set count that run's questions, and a run the server did not start is refused.
     */
    public record QuestionRequest(
            @NotNull(message = "Question is required.")
            @Positive(message = "Question is required.")
            Long questionId,
            LocalDateTime runStartedAt,
            @Size(max = 128, message = "Hint run signature is invalid.")
            String runSignature) {

        public QuestionRequest {
            runSignature = stripToNull(runSignature);
        }
    }

    /**
     * A hint. The interface writes the sentence for {@code kind} in the student's language from the fields;
     * {@code ruledOut} are answers to grey out.
     *
     * @param kind {@code RULE_OUT}, {@code CLUE}, {@code FIRST_SOUND}, {@code FIRST_LETTER}, {@code TWO_LEFT},
     *             {@code FIRST_TILE}, {@code LEAVE_OUT}, {@code ORDER_START}, {@code INITIALS}, {@code TYPE_THIS},
     *             {@code PAIR}, {@code STROKE_COUNT} or {@code STROKE_ORDER}
     * @param words the answers or tiles the sentence names, in order
     * @param text a clue in words (an explanation with the answer left out) or a sound ("x", "xiexie")
     * @param count a number the sentence names (syllables, strokes)
     */
    @Schema(name = "HintStep")
    public record Step(
            String kind,
            List<String> words,
            List<String> pinyin,
            String text,
            Integer count,
            List<String> ruledOut) {
    }

    /** The words the typed pinyin spells, in the order a keyboard would offer them; empty when it spells none. */
    public record Candidates(List<String> words) {
    }

    public record CandidatesRequest(
            @NotBlank(message = "Item is required.")
            @Size(max = 40, message = "Item is required.")
            String itemKey,
            @NotBlank(message = "Type the pinyin first.")
            @Size(max = 200, message = "Pinyin must not exceed 200 characters.")
            String pinyin) {

        public CandidatesRequest {
            itemKey = strip(itemKey);
            pinyin = strip(pinyin);
        }
    }
}
