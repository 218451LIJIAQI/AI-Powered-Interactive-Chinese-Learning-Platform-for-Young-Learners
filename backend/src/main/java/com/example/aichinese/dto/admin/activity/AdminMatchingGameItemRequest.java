package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One left/right pair inside an admin-managed matching game.
 *
 * <p>Replayed as part of the parent {@link AdminMatchingGameRequest}
 * so the whole game can be edited in a single transaction:
 * {@code AdminActivityService.updateMatchingGame} deletes the entire
 * existing children set and re-inserts the incoming list inside the
 * same JPA transaction, so the "ghost item" partial-failure state that
 * an item-by-item endpoint would expose is impossible here.</p>
 *
 * <h2>Image vs text pairs</h2>
 * <p>{@link #leftImageUrl} / {@link #rightImageUrl} are optional — when
 * present, the front-end shows the image instead of the text. Authors
 * can mix-and-match: text on the left, image on the right is a common
 * "label this picture" variant; image on both sides is a
 * "match the photo" variant. {@link #pinyin} is shown beneath the left
 * card to scaffold beginners who can read pinyin but not yet recognise
 * the Chinese form (the 255-char ceiling accommodates multi-syllable
 * phrases or annotated tone marks).</p>
 *
 * <p>Both image URL fields are constrained to {@code http(s)://}
 * absolute URLs (or empty) by the same {@code @Pattern} the rest of
 * the platform uses; this rejects {@code javascript:}, {@code data:},
 * {@code file:} and protocol-relative URLs that could otherwise reach
 * learner browsers as cross-site script or local-file disclosure
 * vectors.</p>
 *
 * <p>{@link #sortOrder} sets the canonical authoring order; the
 * student-facing front-end shuffles each play so the learner cannot
 * memorise positional answers. The upper {@code @Max} bound is a
 * defensive cap, not a business rule.</p>
 *
 * @param leftText      left-side prompt; required, max 500 chars
 *                      ({@code matching_game_items.left_text VARCHAR(500)})
 * @param rightText     right-side prompt; required, max 500 chars
 *                      ({@code matching_game_items.right_text VARCHAR(500)})
 * @param leftImageUrl  optional left-side image; max 255 chars; must be
 *                      {@code http(s)://...} or empty/{@code null}
 *                      ({@code matching_game_items.left_image_url VARCHAR(255)})
 * @param rightImageUrl optional right-side image; max 255 chars; must be
 *                      {@code http(s)://...} or empty/{@code null}
 *                      ({@code matching_game_items.right_image_url VARCHAR(255)})
 * @param pinyin        optional pinyin scaffold for the left side; max
 *                      255 chars
 *                      ({@code matching_game_items.pinyin VARCHAR(255)})
 * @param sortOrder     authoring order, {@code 0..10000}; nullable
 *                      (mapper falls back to {@code 0})
 */
public record AdminMatchingGameItemRequest(
        @NotBlank(message = "Left text is required.")
        @Size(max = 500, message = "Left text must not exceed 500 characters.")
        String leftText,

        @NotBlank(message = "Right text is required.")
        @Size(max = 500, message = "Right text must not exceed 500 characters.")
        String rightText,

        @Pattern(
                regexp = AdminMatchingGameItemRequest.HTTP_URL_PATTERN,
                message = "Left image URL must start with http:// or https://.")
        @Size(max = 255, message = "Left image URL must not exceed 255 characters.")
        String leftImageUrl,

        @Pattern(
                regexp = AdminMatchingGameItemRequest.HTTP_URL_PATTERN,
                message = "Right image URL must start with http:// or https://.")
        @Size(max = 255, message = "Right image URL must not exceed 255 characters.")
        String rightImageUrl,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {

    public AdminMatchingGameItemRequest {
        leftText = strip(leftText);
        rightText = strip(rightText);
        leftImageUrl = stripToNull(leftImageUrl);
        rightImageUrl = stripToNull(rightImageUrl);
        pinyin = stripToNull(pinyin);
    }

    /**
     * Compile-time-constant regex used by the {@link Pattern} guards on
     * {@link #leftImageUrl} and {@link #rightImageUrl}. Accepts only
     * absolute {@code http(s)://...} URLs (or empty/{@code null}) so
     * that {@code javascript:}, {@code data:}, {@code file:} and
     * protocol-relative URLs cannot reach learner browsers.
     *
     * <p>Centralising the literal here removes the only in-file
     * duplication; the regex itself intentionally matches the
     * project-wide convention used in
     * {@code RecordedLessonVideoRequest} et al. so a future tightening
     * (e.g. requiring a TLD) can be coordinated across all DTOs.</p>
     */
    static final String HTTP_URL_PATTERN = "^(https?://.+)?$";
}
