package com.example.aichinese.dto.admin.activity;

/**
 * Read-only mirror of {@link AdminMatchingGameItemRequest} returned
 * inside the parent {@link AdminMatchingGameResponse#items()} list.
 *
 * <p>Adds the database-assigned {@link #id} so the front-end can
 * distinguish "edit existing item" from "insert new item" when the
 * admin re-saves the game — the parent endpoint replays the entire
 * children set on update, and the {@code id} is what lets the UI
 * preserve unchanged rows visually instead of treating every save as
 * a full re-author. Items are emitted in
 * {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code MatchingGameItemRepository}) so the admin sees the same
 * canonical ordering as the student-facing renderer would before
 * per-attempt shuffling.</p>
 *
 * <p>This record carries no validation annotations: it is server-built
 * from already-persisted data, never bound from a request body, and
 * being a {@code record} it is structurally immutable.</p>
 *
 * @param id             database-assigned primary key, never
 *                       {@code null} for any row that has been persisted
 * @param leftText       left-side prompt, max 500 chars
 *                       ({@code matching_game_items.left_text})
 * @param rightText      right-side prompt, max 500 chars
 *                       ({@code matching_game_items.right_text})
 * @param leftImageUrl   optional left-side image; {@code null} when
 *                       unset; max 255 chars
 * @param rightImageUrl  optional right-side image; {@code null} when
 *                       unset; max 255 chars
 * @param pinyin         optional pinyin scaffold for the left side;
 *                       max 255 chars
 * @param sortOrder      canonical authoring order, {@code 0..10000}
 */
public record AdminMatchingGameItemResponse(
        Long id,
        String leftText,
        String rightText,
        String leftImageUrl,
        String rightImageUrl,
        String pinyin,
        Integer sortOrder) {
}
