package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.immutableListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request bodies of the class endpoints (admin class authoring, the learner's class activities). */
public final class ClassRequests {

    private ClassRequests() {
    }

    /**
     * Create a class, or save its details (they go live at once, also on a published class). The category and "best
     * after" are optional: blank clears them.
     */
    public record ClassDetails(
            @NotBlank(message = "Class title is required.")
            @Size(max = 200, message = "Class title must be 200 characters or fewer.")
            String title,

            @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
            String description,

            @Positive(message = "Category id must be positive.")
            Long categoryId,

            @Min(value = 1, message = "Best after unit must be between 1 and 8.")
            @Max(value = 8, message = "Best after unit must be between 1 and 8.")
            Integer bestAfterUnit) {

        public ClassDetails {
            title = strip(title);
            description = stripToNull(description);
        }
    }

    public record UnitTitle(
            @NotBlank(message = "Unit title is required.")
            @Size(max = 200, message = "Unit title must be 200 characters or fewer.")
            String title) {

        public UnitTitle {
            title = strip(title);
        }
    }

    public record LessonTitle(
            @NotBlank(message = "Lesson title is required.")
            @Size(max = 200, message = "Lesson title must be 200 characters or fewer.")
            String title) {

        public LessonTitle {
            title = strip(title);
        }
    }

    @Schema(name = "ClassKeyPhraseInput")
    public record KeyPhrase(
            @NotBlank(message = "Chinese is required for every key phrase.")
            @Size(max = 100, message = "Chinese must be 100 characters or fewer.")
            String chinese,

            @NotBlank(message = "Pinyin is required for every key phrase.")
            @Size(max = 200, message = "Pinyin must be 200 characters or fewer.")
            String pinyin,

            @NotBlank(message = "Meaning is required for every key phrase.")
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.")
            String meaning) {

        public KeyPhrase {
            chinese = strip(chinese);
            pinyin = strip(pinyin);
            meaning = strip(meaning);
        }
    }

    /** The Content tab of the lesson editor: details and key phrases, saved together. */
    public record LessonContent(
            @NotBlank(message = "Lesson title is required.")
            @Size(max = 200, message = "Lesson title must be 200 characters or fewer.")
            String title,

            @Positive(message = "Unit id must be positive.")
            Long unitId,

            @Min(value = 1, message = "Minutes must be between 1 and 240.")
            @Max(value = 240, message = "Minutes must be between 1 and 240.")
            Integer minutes,

            @Size(max = 1000, message = "Summary must be 1000 characters or fewer.")
            String summary,

            @Size(max = 30, message = "A lesson can have at most 30 key phrases.")
            @Valid
            List<@NotNull(message = "Key phrase must not be null.") KeyPhrase> keyPhrases) {

        public LessonContent {
            title = strip(title);
            summary = stripToNull(summary);
            keyPhrases = immutableListOrEmpty(keyPhrases);
        }
    }

    @Schema(name = "ClassTraceCharacterInput")
    public record TraceCharacter(
            @NotBlank(message = "Enter the character to trace.")
            @Size(max = 4, message = "Enter one character per row.")
            String character,

            @NotBlank(message = "Pinyin is required for every character.")
            @Size(max = 40, message = "Pinyin must be 40 characters or fewer.")
            String pinyin,

            @Size(max = 80, message = "Meaning must be 80 characters or fewer.")
            String meaning) {

        public TraceCharacter {
            character = strip(character);
            pinyin = strip(pinyin);
            meaning = stripToNull(meaning);
        }
    }

    @Schema(name = "ClassMatchPairInput")
    public record MatchPair(
            @NotBlank(message = "Chinese is required for every pair.")
            @Size(max = 100, message = "Chinese must be 100 characters or fewer.")
            String chinese,

            @NotBlank(message = "Pinyin is required for every pair.")
            @Size(max = 200, message = "Pinyin must be 200 characters or fewer.")
            String pinyin,

            @NotBlank(message = "Meaning is required for every pair.")
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.")
            String meaning) {

        public MatchPair {
            chinese = strip(chinese);
            pinyin = strip(pinyin);
            meaning = strip(meaning);
        }
    }

    /** The settings of a lesson's quiz: "Allow SparkBot hints" (learners stuck on a question can open its hint). */
    public record QuizSettings(
            @NotNull(message = "Choose whether learners can get hints in this quiz.")
            Boolean hintsAllowed) {
    }

    /** A card of a lesson's Flashcards activity: learners see the word, then flip for its pinyin and meaning (A3.16). */
    public record Card(
            @NotBlank(message = "Chinese is required for every card.")
            @Size(max = 100, message = "Chinese must be 100 characters or fewer.")
            String chinese,

            @NotBlank(message = "Pinyin is required for every card.")
            @Size(max = 200, message = "Pinyin must be 200 characters or fewer.")
            String pinyin,

            @NotBlank(message = "Meaning is required for every card.")
            @Size(max = 200, message = "Meaning must be 200 characters or fewer.")
            String meaning) {

        public Card {
            chinese = strip(chinese);
            pinyin = strip(pinyin);
            meaning = strip(meaning);
        }
    }

    /**
     * An activity of a lesson. Flashcards have their {@code cards} (without any, learners review the lesson's key
     * phrases); tracing needs {@code characters} and matching needs {@code pairs}.
     */
    @Schema(name = "ClassActivityInput")
    public record Activity(
            @NotBlank(message = "Activity type is required.")
            @Size(max = 20, message = "Activity type must be 20 characters or fewer.")
            String type,

            @NotBlank(message = "Activity title is required.")
            @Size(max = 200, message = "Activity title must be 200 characters or fewer.")
            String title,

            @Size(max = 8, message = "An activity can have at most 8 characters to trace.")
            @Valid
            List<@NotNull(message = "Character must not be null.") TraceCharacter> characters,

            @Size(max = 12, message = "A matching activity can have at most 12 pairs.")
            @Valid
            List<@NotNull(message = "Pair must not be null.") MatchPair> pairs,

            @Size(max = 20, message = "A flashcards activity can have at most 20 cards.")
            @Valid
            List<@NotNull(message = "Card must not be null.") Card> cards) {

        public Activity {
            type = strip(type);
            title = strip(title);
            characters = immutableListOrEmpty(characters);
            pairs = immutableListOrEmpty(pairs);
            cards = immutableListOrEmpty(cards);
        }
    }

    /** The learner's pairs of a matching activity: each Chinese word with the meaning chosen for it. */
    public record MatchingAnswer(
            @NotNull(message = "Pairs are required.")
            @Size(max = 50, message = "Too many pairs.")
            @Valid
            List<@NotNull(message = "Pair must not be null.") ChosenPair> pairs) {

        public MatchingAnswer {
            pairs = immutableListOrNull(pairs);
        }
    }

    public record ChosenPair(
            @NotNull(message = "Word id is required.")
            @Positive(message = "Word id is required.")
            Long wordId,
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.")
            String meaning) {

        public ChosenPair {
            meaning = strip(meaning);
        }
    }

    public record MatchingHint(
            @NotNull(message = "Word id is required.")
            @Positive(message = "Word id is required.")
            Long wordId) {
    }

    public record HintRequest(
            @NotNull(message = "Question id is required.")
            @Positive(message = "Question id is required.")
            Long questionId) {
    }
}
