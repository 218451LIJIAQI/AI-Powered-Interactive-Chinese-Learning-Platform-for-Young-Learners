package com.example.aichinese.dto.admin.activity;

/**
 * Read-only mirror of {@link AdminDragDropItemRequest} returned inside
 * the parent {@link AdminDragDropActivityResponse#items()} list.
 *
 * <p>Adds the database-assigned {@link #id} so the front-end can
 * distinguish "edit existing card" from "insert new card" when the
 * admin re-saves the activity. Items are emitted in the canonical
 * {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code DragDropItemRepository}) so the admin preview matches what
 * the student's renderer sees before optional shuffling.</p>
 *
 * <p>This record carries no validation annotations: it is server-built
 * from already-persisted data, never bound from a request body, and
 * being a {@code record} it is structurally immutable.</p>
 *
 * @param id         database-assigned primary key
 * @param dragText   visible draggable label
 * @param targetZone label of the drop zone the card must land in
 * @param pinyin     optional pinyin scaffold; {@code null} when unset
 * @param imageUrl   optional image URL; {@code null} when unset
 * @param sortOrder  canonical authoring order
 */
public record AdminDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {
}
