package com.example.aichinese.dto.content;

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
 * {@code TeacherPackageService.addPackageItem} from the underlying
 * lesson's/module's title. Surfacing the snapshot rather than a
 * live-joined title means the package's contents render
 * deterministically even when the source content is later renamed
 * — important for catalogues and order receipts that must show
 * exactly what the student bought. The next time the teacher
 * removes and re-adds the item, the snapshot is refreshed with the
 * current title.</p>
 *
 * <p>{@link #displayOrder} drives the canonical render order
 * ({@code ASC}). The student-facing catalogue uses the same order;
 * this is a curated bundle, not a shuffled set.</p>
 */
public record PackageItemResponse(
        Long id,
        Long packageId,
        PackageItemType itemType,
        Long itemId,
        Integer displayOrder,
        String titleSnapshot) {
}
