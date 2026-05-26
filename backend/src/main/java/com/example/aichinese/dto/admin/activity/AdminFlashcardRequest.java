package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/activities/flashcards} (create)
 * and {@code PUT /api/v1/admin/activities/flashcards/{id}} (update).
 *
 * <p>Captures both the card content (front/back, multilingual meanings,
 * media URLs) and the shared activity metadata vocabulary
 * ({@link #hskLevel}, {@link #ageBand}, {@link #difficulty},
 * {@link #topic}, {@link #tags}, {@link #xpReward},
 * {@link #estimatedSeconds}) that the admin fills in when curating the
 * global flashcard library — see
 * {@link AdminActivitySummaryResponse} for the shared metadata contract.</p>
 *
 * <h2>Why {@link #title} is optional</h2>
 * <p>The student-facing flashcard renderer falls back to
 * {@link #frontText} as the card heading when the admin omits a
 * separate title. Admins curating large vocabulary decks can therefore
 * skip the title and let the front text drive both the deck list and
 * the card face — a deliberate ergonomics choice that the
 * {@code AdminActivityLibraryIntegrationTests} validation test
 * relies on.</p>
 *
 * <h2>Why {@link #frontText} and {@link #backText} have asymmetric caps</h2>
 * <p>Front text is the at-a-glance prompt (typically a single Chinese
 * word — well under 500 chars). Back text holds the full explanation
 * with usage examples and mnemonic notes, so it gets a 1000-char cap.
 * Both align with the underlying {@code Flashcard} entity column
 * lengths so the validator and the database agree.</p>
 *
 * <h2>{@link #extraPayload}</h2>
 * <p>Free-form JSON document for forward compatibility — currently
 * holds optional fields like {@code radicals}, {@code strokeOrder},
 * and {@code exampleSentences} without requiring a Flyway migration
 * each time a new field is introduced. The {@code Flashcard.extraPayload}
 * column is {@code LONGTEXT} (≈4&nbsp;GB capacity), so the
 * 200&nbsp;000-char cap is a defensive bound that comfortably fits any
 * realistic structured-content payload while keeping single-row JSON
 * generation predictable. The payload is treated as an opaque blob
 * server-side; the front-end is the canonical schema owner.</p>
 *
 * <h2>HSK level bound</h2>
 * <p>Hanyu Shuiping Kaoshi (HSK) is the standardised Chinese
 * proficiency exam graded 1-6, so the validator caps {@link #hskLevel}
 * at the same range. {@code null} is permitted for level-agnostic
 * cards (general vocabulary, holiday-themed decks, etc.).</p>
 *
 * <h2>Tag list contract</h2>
 * <p>Tags are joined into a single comma-separated {@code VARCHAR(500)}
 * column by {@code AdminActivityMapper#joinTags(List)}. With each tag
 * capped at 50 chars and the list capped at 8 entries, the worst-case
 * stored value (8 × 51 chars including separators) stays comfortably
 * inside the column. Each element is {@code @NotBlank} so admins get
 * an explicit error for typo'd empty tags rather than silently losing
 * them at the mapper.</p>
 *
 * <h2>Media URL pattern</h2>
 * <p>{@link #imageUrl} and {@link #audioUrl} are constrained to
 * {@code http(s)://} absolute URLs (or empty/{@code null}). Relative
 * paths, {@code javascript:}, {@code data:} and protocol-relative
 * URLs are rejected to prevent dangerous schemes reaching learner
 * browsers. This matches the project-wide convention seen in
 * {@code RecordedLessonVideoRequest}.</p>
 *
 * @param title           optional deck/card title; max 200 chars; falls
 *                        back to {@link #frontText} for display
 * @param frontText       at-a-glance card prompt; required; max 500 chars
 * @param backText        full explanation; required; max 1000 chars
 * @param pinyin          optional pinyin scaffold; max 255 chars
 * @param englishMeaning  optional English gloss; max 500 chars
 * @param malayMeaning    optional Bahasa Melayu gloss; max 500 chars
 * @param imageUrl        optional image URL; max 255 chars; must be
 *                        {@code http(s)://} or empty
 * @param audioUrl        optional audio URL; max 255 chars; must be
 *                        {@code http(s)://} or empty
 * @param sortOrder       authoring order, {@code 0..10000}; nullable
 *                        (mapper falls back to {@code 0})
 * @param extraPayload    optional opaque JSON; max 200&nbsp;000 chars
 * @param hskLevel        optional HSK band, {@code 1..6}
 * @param ageBand         optional target age cohort
 * @param difficulty      optional easy/medium/hard tier
 * @param topic           optional short topic label; max 100 chars
 * @param tags            optional list of free-form tags; up to 8
 *                        non-blank entries, each max 50 chars
 * @param xpReward        optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint, {@code 0..86400}
 */
public record AdminFlashcardRequest(
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @NotBlank(message = "Front text is required.")
        @Size(max = 500, message = "Front text must not exceed 500 characters.")
        String frontText,

        @NotBlank(message = "Back text is required.")
        @Size(max = 1000, message = "Back text must not exceed 1000 characters.")
        String backText,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @Size(max = 500, message = "English meaning must not exceed 500 characters.")
        String englishMeaning,

        @Size(max = 500, message = "Bahasa Melayu meaning must not exceed 500 characters.")
        String malayMeaning,

        @Pattern(
                regexp = AdminFlashcardRequest.HTTP_URL_PATTERN,
                message = "Image URL must start with http:// or https://.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

        @Pattern(
                regexp = AdminFlashcardRequest.HTTP_URL_PATTERN,
                message = "Audio URL must start with http:// or https://.")
        @Size(max = 255, message = "Audio URL must not exceed 255 characters.")
        String audioUrl,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder,

        @Size(max = 200_000, message = "Extra payload is too large.")
        String extraPayload,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

        AgeBand ageBand,

        ActivityDifficulty difficulty,

        @Size(max = 100, message = "Topic must not exceed 100 characters.")
        String topic,

        @Size(max = 8, message = "At most 8 tags are allowed.")
        List<
                @NotBlank(message = "Tag must not be blank.")
                @Size(max = 50, message = "Each tag must not exceed 50 characters.")
                String> tags,

        @Min(value = 0, message = "XP reward must be non-negative.")
        @Max(value = 1000, message = "XP reward must not exceed 1000.")
        Integer xpReward,

        @Min(value = 0, message = "Estimated seconds must be non-negative.")
        @Max(value = 86_400, message = "Estimated seconds must not exceed 86400 (24 hours).")
        Integer estimatedSeconds) {

    public AdminFlashcardRequest {
        title = stripToNull(title);
        frontText = strip(frontText);
        backText = strip(backText);
        pinyin = stripToNull(pinyin);
        englishMeaning = stripToNull(englishMeaning);
        malayMeaning = stripToNull(malayMeaning);
        imageUrl = stripToNull(imageUrl);
        audioUrl = stripToNull(audioUrl);
        extraPayload = stripToNull(extraPayload);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
    }

    /**
     * Compile-time-constant regex used by the {@link Pattern} guards on
     * {@link #imageUrl} and {@link #audioUrl}. Accepts only absolute
     * {@code http(s)://...} URLs (or empty/{@code null}) so that
     * {@code javascript:}, {@code data:}, {@code file:} and protocol-
     * relative URLs cannot reach learner browsers.
     *
     * <p>Centralising the literal here removes the only in-file
     * duplication; the regex itself intentionally matches the
     * project-wide convention used in
     * {@code RecordedLessonVideoRequest} et al. so a future tightening
     * (e.g. requiring a TLD) can be coordinated across all DTOs.</p>
     */
    static final String HTTP_URL_PATTERN = "^(https?://.+)?$";
}
