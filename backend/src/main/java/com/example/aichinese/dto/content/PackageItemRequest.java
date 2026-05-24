package com.example.aichinese.dto.content;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * the corresponding row in the {@code learning_modules} or
 * {@code lessons} table (selected by {@code itemType}).</p>
 *
 * <p>The service
 * ({@link com.example.aichinese.service.TeacherPackageService#addPackageItem}):</p>
 * <ul>
 *   <li>Validates the parent package is owned by the calling
 *       teacher and is in an editable state.</li>
 *   <li>Validates the referenced module / lesson is also owned by
 *       the same teacher (you cannot bundle another teacher's
 *       content).</li>
 *   <li>Captures a {@code titleSnapshot} of the underlying row's
 *       title at insert time — see
 *       {@link PackageItemResponse#titleSnapshot()} for why.</li>
 * </ul>
 *
 * <p>{@link #displayOrder} defaults to {@code 0} server-side when
 * omitted (see {@code TeacherPackageService.addPackageItem}); the
 * front-end typically supplies the next-highest value to
 * append-at-end.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param itemType     {@code MODULE} or {@code LESSON} — selects
 *                     which sibling table the {@link #itemId}
 *                     refers to; required
 * @param itemId       primary key of the {@link #itemType}'s row;
 *                     required and must be positive
 * @param displayOrder authoring order ({@code ASC}); nullable
 *                     (server defaults to {@code 0}); when set
 *                     must be {@code >= 0}
 */
@JsonPropertyOrder({ "itemType", "itemId", "displayOrder" })
public record PackageItemRequest(
        @NotNull(message = "Item type is required.")
        PackageItemType itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
