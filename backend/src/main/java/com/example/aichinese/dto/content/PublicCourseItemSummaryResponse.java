package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.ManagedContentType;

/**
 * Compact "what's inside this package" item, surfaced inside
 * {@link PublicCourseDetailResponse#packageItems()}.
 *
 * <p>The public-facing analogue of {@link PackageItemResponse}.
 * Strictly leaner because un-authenticated visitors do not need
 * the package-item row id (cannot edit) or the {@code packageId}
 * (already in the parent payload); only the underlying lesson /
 * module reference plus the order matters.</p>
 *
 * <p>{@link #type} is {@link ManagedContentType} (a superset of
 * {@link com.example.aichinese.entity.enums.PackageItemType} that
 * also covers packages, but the public catalogue never bundles
 * packages — see the no-nesting rule on
 * {@link com.example.aichinese.entity.enums.PackageItemType}).
 * Carries {@code MODULE} or {@code LESSON} only in practice.</p>
 *
 * <p>{@link #title} is the {@code titleSnapshot} preserved from
 * insert time (see {@link PackageItemResponse#titleSnapshot()}) so
 * the catalogue page renders deterministically even when the
 * underlying content is later renamed.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the fields the
 * mapper guarantees from any persisted {@code package_items} row
 * ({@link #type}, {@link #contentId}, {@link #displayOrder}).
 * {@link #title} may legitimately be {@code null} on legacy rows
 * authored before the {@code titleSnapshot} feature shipped — the
 * front-end falls back to a generic "Untitled" label in that case.</p>
 *
 * @param type         {@code MODULE} or {@code LESSON} — package
 *                     items never carry {@code PACKAGE} by
 *                     construction (no-nesting rule); never
 *                     {@code null}
 * @param contentId    primary key of the underlying row in the
 *                     {@link #type}-selected sibling table; never
 *                     {@code null}
 * @param title        snapshot of the underlying row's title
 *                     captured at bundle-insert time; nullable on
 *                     legacy rows
 * @param displayOrder canonical render order ({@code ASC}); never
 *                     {@code null}
 */
@JsonPropertyOrder({ "type", "contentId", "title", "displayOrder" })
public record PublicCourseItemSummaryResponse(
        ManagedContentType type,
        Long contentId,
        String title,
        Integer displayOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * NOT NULL schema columns so a malformed bundled-item entry
     * cannot reach the wire format and break the catalogue's
     * "what's inside this package" panel.
     */
    public PublicCourseItemSummaryResponse {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
