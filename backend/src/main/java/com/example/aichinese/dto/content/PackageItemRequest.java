package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.PackageItemType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body for {@code POST /api/v1/teachers/packages/{id}/items} — the
 * teacher appends one item (a module or a lesson) to an existing
 * course package.
 *
 * <p>Removal uses the symmetric
 * {@code DELETE /api/v1/teachers/packages/{id}/items/{itemId}}
 * (no body); reordering is currently a remove + add cycle, but the
 * record-shaped {@link #displayOrder} field allows for an in-place
 * patch endpoint to be added later without breaking the wire
 * format.</p>
 *
 * <h2>{@link #itemType} / {@link #itemId}</h2>
 * <p>{@link #itemType} is the
 * {@link PackageItemType} enum (constants {@code MODULE} or
 * {@code LESSON}) — strictly a subset of
 * {@link com.example.aichinese.entity.enums.ManagedContentType} that
 * intentionally excludes {@code PACKAGE} to prevent cyclic /
 * nested-package edge cases. {@link #itemId} is the primary key of
 * the corresponding row.</p>
 *
 * <p>The service ({@code TeacherPackageService.addPackageItem}):</p>
 * <ul>
 *   <li>Validates the parent package is owned by the calling
 *       teacher and is in an editable state.</li>
 *   <li>Validates the referenced module/lesson is also owned by the
 *       same teacher (you cannot bundle another teacher's content).</li>
 *   <li>Captures a {@code titleSnapshot} of the underlying row's
 *       title at insert time — see
 *       {@link PackageItemResponse#titleSnapshot()} for why.</li>
 * </ul>
 *
 * <p>{@link #displayOrder} defaults to {@code 0} server-side when
 * omitted; the front-end typically supplies the next-highest value
 * to append-at-end.</p>
 */
public record PackageItemRequest(
        @NotNull(message = "Item type is required.")
        PackageItemType itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
