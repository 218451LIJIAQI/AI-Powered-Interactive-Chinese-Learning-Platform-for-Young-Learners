package com.example.aichinese.dto.admin.activity;

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
 * {@code AdminActivityService.saveMatchingGameItems} deletes any
 * children not present in the new request and inserts/updates the
 * remainder, all inside the same JPA transaction. This avoids the
 * "ghost item" problem that an item-by-item endpoint would have.</p>
 *
 * <h2>Image vs text pairs</h2>
 * <p>{@link #leftImageUrl} / {@link #rightImageUrl} are optional — when
 * present, the front-end shows the image instead of the text. Authors
 * can mix-and-match: text on the left, image on the right is a common
 * "label this picture" variant; image on both sides is a
 * "match the photo" variant. {@link #pinyin} is shown beneath the left
 * card to scaffold beginners who can read pinyin but not yet recognise
 * the Chinese form.</p>
 *
 * <p>Both image URL fields are constrained to {@code http(s)://}
 * absolute URLs (or empty) by the same {@code @Pattern} the rest of
 * the platform uses; this rejects {@code javascript:}, {@code data:},
 * {@code file:} and protocol-relative URLs that could otherwise reach
 * learner browsers.</p>
 *
 * <p>{@link #sortOrder} sets the canonical authoring order; the
 * student-facing front-end shuffles each play so the learner cannot
 * memorise positional answers. The upper {@code @Max} bound is a
 * defensive cap, not a business rule.</p>
 *
 * @param leftText      left-side prompt; required, max 500 chars, matches
 *                      {@code matching_game_items.left_text}
 * @param rightText     right-side prompt; required, max 500 chars, matches
 *                      {@code matching_game_items.right_text}
 * @param leftImageUrl  optional left-side image; max 255 chars; must be
 *                      {@code http(s)://...} or empty/{@code null}
 * @param rightImageUrl optional right-side image; max 255 chars; must be
 *                      {@code http(s)://...} or empty/{@code null}
 * @param pinyin        optional pinyin scaffold for the left side; max
 *                      255 chars
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
                regexp = "^(https?://.+)?$",
                message = "Left image URL must start with http:// or https://.")
        @Size(max = 255, message = "Left image URL must not exceed 255 characters.")
        String leftImageUrl,

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Right image URL must start with http:// or https://.")
        @Size(max = 255, message = "Right image URL must not exceed 255 characters.")
        String rightImageUrl,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {
}
