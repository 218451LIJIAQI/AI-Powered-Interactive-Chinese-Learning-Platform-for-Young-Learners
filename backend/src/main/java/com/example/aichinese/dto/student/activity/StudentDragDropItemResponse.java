package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/**
 * One draggable card inside a student-facing drag-and-drop activity,
 * surfaced as part of {@link StudentDragDropActivityResponse#items()}.
 *
 * <p>{@link #dragText} is the visible label the learner picks up;
 * {@link #targetZone} is the label of the drop area it must land in.
 * Several cards may share the same {@code targetZone} when the
 * front-end presents a multi-slot drop area (e.g. categorise four
 * fruits into a single "Fruit" zone). {@link #pinyin} scaffolds
 * beginners; {@link #imageUrl} optionally replaces or supplements the
 * text on the draggable card.</p>
 *
 * <p>Note: the student response intentionally exposes
 * {@link #targetZone} on the wire because client-side drop validation
 * needs it to render a "Correct!" pulse before the activity is
 * formally completed. The server still re-validates on the
 * {@code /complete} call, so the client's local check is purely a UX
 * affordance — it cannot be used to game XP because the
 * {@code completePlatformActivity} idempotency rule (see
 * {@link StudentActivityCompletionResponse}) only awards points once.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on {@link #id},
 * {@link #dragText} and {@link #targetZone} — they map to NOT NULL
 * schema columns and are required by the rendering and validation
 * paths. {@link #pinyin}, {@link #imageUrl} and {@link #sortOrder} may
 * legitimately be {@code null} when the author left them unset.</p>
 *
 * @param id          database-assigned primary key; never
 *                    {@code null}
 * @param dragText    visible label on the draggable card; never
 *                    {@code null}
 * @param targetZone  label of the drop area this card must land in;
 *                    never {@code null}
 * @param pinyin      optional pinyin scaffold; nullable
 * @param imageUrl    optional image; nullable
 * @param sortOrder   canonical authoring order; nullable when the
 *                    author did not pin a sort position
 */
public record StudentDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * required schema columns ({@code id}, {@code dragText},
     * {@code targetZone}) so a malformed item cannot reach the wire
     * format and break the front-end drop-validation logic.
     */
    public StudentDragDropItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(dragText, "dragText must not be null");
        Objects.requireNonNull(targetZone, "targetZone must not be null");
    }
}
