package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PackageItemType;

/**
 * One item inside a course package, surfaced inside
 * {@link CoursePackageResponse#packageItems()}.
 *
 * <p>Carries both the package-item row's own id ({@link #id},
 * {@code package_items.id}) and the underlying content's id
 * ({@link #itemId}, the {@code learning_modules.id} or
 * {@code lessons.id} depending on {@link #itemType}). The two are
 * needed for different actions:</p>
 * <ul>
 *   <li>{@link #id} — used by
 *       {@code DELETE /api/v1/teachers/packages/{packageId}/items/{itemId}}
 *       to remove the row.</li>
 *   <li>{@link #itemId} — used by the front-end to deep-link to
 *       the underlying lesson/module preview.</li>
 * </ul>
 *
 * <h2>{@link #titleSnapshot}</h2>
 * <p>Frozen at insert time by
 * {@link com.example.aichinese.service.TeacherPackageService#addPackageItem}
 * from the underlying lesson's / module's title. Surfacing the
 * snapshot rather than a live-joined title means the package's
 * contents render deterministically even when the source content
 * is later renamed — important for catalogues and order receipts
 * that must show exactly what the student bought. The next time
 * the teacher removes and re-adds the item, the snapshot is
 * refreshed with the current title.</p>
 *
 * <p>{@link #displayOrder} drives the canonical render order
 * ({@code ASC}). The student-facing catalogue uses the same order;
 * this is a curated bundle, not a shuffled set.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted {@code package_items} row
 * ({@link #id}, {@link #itemType}, {@link #itemId},
 * {@link #displayOrder}). {@link #packageId} is nullable to
 * accommodate the rare orphaned row (the content mapper drops the FK
 * when the parent association failed to hydrate).
 * {@link #titleSnapshot} may legitimately be {@code null} on legacy
 * rows authored before the snapshot feature shipped.</p>
 *
 * @param id            row id of the {@code package_items} entry;
 *                      never {@code null}
 * @param packageId     parent {@code course_packages.id};
 *                      {@code null} only when the parent
 *                      association failed to hydrate (anomalous)
 * @param itemType      {@code MODULE} or {@code LESSON}; selects
 *                      which sibling table {@link #itemId} refers
 *                      to; NOT NULL schema column; never
 *                      {@code null}
 * @param itemId        primary key of the underlying row in the
 *                      {@link #itemType}-selected sibling table;
 *                      NOT NULL schema column; never {@code null}
 * @param displayOrder  canonical render order ({@code ASC});
 *                      NOT NULL schema column with a default of
 *                      {@code 0}; never {@code null}
 * @param titleSnapshot frozen title captured at insert time;
 *                      nullable on legacy rows authored before the
 *                      snapshot feature shipped
 */
@JsonPropertyOrder({
        "id",
        "packageId",
        "itemType",
        "itemId",
        "displayOrder",
        "titleSnapshot"
})
public record PackageItemResponse(
        Long id,
        Long packageId,
        PackageItemType itemType,
        Long itemId,
        Integer displayOrder,
        String titleSnapshot) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * NOT NULL schema columns so a malformed package-item row
     * cannot reach the wire format and break either the teacher
     * dashboard or the public catalogue's "what's inside this
     * package" panel.
     */
    public PackageItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(itemType, "itemType must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
