package com.example.aichinese.dto.admin.activity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One draggable card inside an admin-managed drag-and-drop activity.
 *
 * <p>Replayed as part of the parent
 * {@link AdminDragDropActivityRequest} so the whole activity can be
 * edited in a single transaction —
 * {@code AdminActivityService.saveDragDropItems} reconciles the
 * incoming list against the persisted children, deleting absent rows
 * and inserting/updating the remainder atomically.</p>
 *
 * <p>{@link #dragText} is the visible label the learner picks up;
 * {@link #targetZone} is the label of the drop area it must land in.
 * Several cards may share the same {@code targetZone} when the
 * front-end presents a multi-slot drop area (e.g. categorise four
 * fruits into a single "Fruit" zone).</p>
 *
 * <p>{@link #pinyin} scaffolds beginners; {@link #imageUrl} optionally
 * replaces or supplements the text on the draggable card. The
 * {@code @Pattern} on {@link #imageUrl} mirrors the project-wide
 * convention (see {@code RecordedLessonVideoRequest}) and rejects
 * {@code javascript:}, {@code data:}, {@code file:} and protocol-
 * relative URLs that could otherwise reach learner browsers.</p>
 *
 * <p>{@link #sortOrder} sets the canonical authoring order; the
 * student-facing renderer is free to shuffle on each play to prevent
 * positional memorisation. The upper {@code @Max} bound is a defensive
 * cap rather than a business rule — there is no realistic scenario in
 * which an activity needs sort positions in the thousands, so the
 * bound stops abusive clients before the value reaches the database.</p>
 *
 * @param dragText   visible draggable label; required, max 500 chars,
 *                   matches the underlying {@code drag_drop_items.drag_text}
 *                   column length
 * @param targetZone label of the drop zone the card must land in;
 *                   required, max 200 chars, matches
 *                   {@code drag_drop_items.target_zone}
 * @param pinyin     optional pinyin scaffold; max 255 chars
 * @param imageUrl   optional image URL; max 255 chars; must be
 *                   {@code http(s)://...} or empty/{@code null}
 * @param sortOrder  authoring order, {@code 0..10000}; nullable
 *                   (mapper falls back to {@code 0})
 */
public record AdminDragDropItemRequest(
        @NotBlank(message = "Drag text is required.")
        @Size(max = 500, message = "Drag text must not exceed 500 characters.")
        String dragText,

        @NotBlank(message = "Target zone is required.")
        @Size(max = 200, message = "Target zone must not exceed 200 characters.")
        String targetZone,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Image URL must start with http:// or https://.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {
}
