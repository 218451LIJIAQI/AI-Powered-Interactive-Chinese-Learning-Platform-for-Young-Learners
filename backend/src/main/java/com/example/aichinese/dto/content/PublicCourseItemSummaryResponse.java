package com.example.aichinese.dto.content;

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
 */
public record PublicCourseItemSummaryResponse(
        ManagedContentType type,
        Long contentId,
        String title,
        Integer displayOrder) {
}
