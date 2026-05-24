package com.example.aichinese.dto.student.activity;

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
 */
public record StudentDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {
}
