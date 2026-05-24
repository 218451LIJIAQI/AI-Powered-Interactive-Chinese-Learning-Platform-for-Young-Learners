package com.example.aichinese.dto.admin.activity;

/**
 * Read-only mirror of {@link AdminMatchingGameItemRequest} returned
 * inside the parent {@link AdminMatchingGameResponse#items()} list.
 *
 * <p>Adds the database-assigned {@link #id} so the front-end can
 * distinguish "edit existing item" from "insert new item" when the
 * admin re-saves the game. Items are emitted in
 * {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code MatchingGameItemRepository}) so the admin sees the same
 * canonical ordering as the student-facing renderer would before
 * per-attempt shuffling.</p>
 *
 * <p>This record carries no validation annotations: it is server-built
 * from already-persisted data, never bound from a request body, and
 * being a {@code record} it is structurally immutable.</p>
 *
 * @param id             database-assigned primary key
 * @param leftText       left-side prompt
 * @param rightText      right-side prompt
 * @param leftImageUrl   optional left-side image; {@code null} when unset
 * @param rightImageUrl  optional right-side image; {@code null} when unset
 * @param pinyin         optional pinyin scaffold for the left side
 * @param sortOrder      canonical authoring order
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
