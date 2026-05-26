package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/activities/story-activities}
 * (create) and
 * {@code PUT /api/v1/admin/activities/story-activities/{id}} (update).
 *
 * <p>Aggregate-root payload bundling the story body
 * ({@link #storyText}, optional {@link #pinyin} and
 * {@link #translation}, optional {@link #imageUrl}) together with the
 * full set of {@link AdminStoryQuestionRequest} comprehension prompts.
 * The {@link jakarta.validation.Valid} on {@link #questions} cascades
 * Bean Validation into each question so a single atomic 400 response
 * surfaces every parent <em>and</em> child violation — there is no
 * partial-success state where the story body would persist while a
 * question is rejected.</p>
 *
 * <h2>Why Pinyin and translation are stored as text</h2>
 * <p>Both are free-text fields rather than per-sentence structured
 * arrays so that authoring stays simple — admins paste the full
 * Pinyin transcription and the full translation as single blobs, and
 * the front-end is responsible for word-level alignment heuristics if
 * required. Both back onto MySQL {@code TEXT} columns (65&nbsp;535
 * <em>byte</em> limit). With {@code utf8mb4} reserving up to four
 * bytes per character, the conservative 32&nbsp;000-char cap
 * guarantees the value always fits — the previous 65&nbsp;535-char
 * cap could silently overflow the column for multi-byte content.</p>
 *
 * <h2>{@link #storyText} sizing</h2>
 * <p>{@code storyText} backs onto a {@code LONGTEXT} column (≈4&nbsp;GB
 * capacity); the 200&nbsp;000-char cap is a defensive bound that
 * accommodates even unusually long short-stories while protecting the
 * server from runaway payloads.</p>
 *
 * <h2>Why questions are optional</h2>
 * <p>{@link #questions} may be {@code null} or empty during initial
 * authoring (admin saves the story body first, then adds prompts);
 * the publication gate refuses to flip the status to
 * {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * until at least one question exists. The upper {@code @Size(max=50)}
 * is a defensive bound — realistic short-story activities carry under
 * 10 comprehension prompts.</p>
 *
 * <p>{@link #imageUrl} is constrained to {@code http(s)://} absolute
 * URLs (or empty) to reject {@code javascript:}, {@code data:},
 * {@code file:} and protocol-relative URLs that could otherwise reach
 * learner browsers as cross-site script or local-file disclosure
 * vectors.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary and {@link AdminFlashcardRequest} for the rationale
 * behind the tag-list cap and the {@code estimatedSeconds} upper bound.</p>
 *
 * @param title            story title; required; max 200 chars
 *                         ({@code story_activities.title VARCHAR(200)})
 * @param instructions     optional learner-facing instructions; max
 *                         1000 chars
 *                         ({@code story_activities.instructions VARCHAR(1000)})
 * @param storyText        required Mandarin narrative; max
 *                         200&nbsp;000 chars
 *                         ({@code story_activities.story_text LONGTEXT})
 * @param pinyin           optional pinyin transliteration; max
 *                         32&nbsp;000 chars
 *                         ({@code story_activities.pinyin TEXT})
 * @param translation      optional English/Bahasa translation; max
 *                         32&nbsp;000 chars
 *                         ({@code story_activities.translation TEXT})
 * @param imageUrl         optional cover image URL; max 255 chars;
 *                         must be {@code http(s)://} or empty
 *                         ({@code story_activities.image_url VARCHAR(255)})
 * @param hskLevel         optional HSK band, {@code 1..6}
 * @param ageBand          optional target age cohort
 * @param difficulty       optional easy/medium/hard tier
 * @param topic            optional short topic label; max 100 chars
 * @param tags             optional list of free-form tags; up to 8
 *                         non-blank entries, each max 50 chars
 * @param xpReward         optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint,
 *                         {@code 0..86400} (24 hours)
 * @param questions        child comprehension prompts; nullable or
 *                         empty during drafting; at most 50 entries;
 *                         each element validated via {@link Valid}
 */
public record AdminStoryActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,

        @NotBlank(message = "Story text is required.")
        @Size(max = 200_000, message = "Story text is too long.")
        String storyText,

        @Size(max = 32_000, message = "Pinyin is too long.")
        String pinyin,

        @Size(max = 32_000, message = "Translation is too long.")
        String translation,

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Image URL must start with http:// or https://.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

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
        Integer estimatedSeconds,

        @Valid
        @Size(max = 50, message = "A story activity cannot contain more than 50 questions.")
        List<@NotNull(message = "Story question must not be null.") AdminStoryQuestionRequest> questions) {

    public AdminStoryActivityRequest {
        title = strip(title);
        instructions = stripToNull(instructions);
        storyText = strip(storyText);
        pinyin = stripToNull(pinyin);
        translation = stripToNull(translation);
        imageUrl = stripToNull(imageUrl);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
        questions = immutableListOrEmpty(questions);
    }
}
