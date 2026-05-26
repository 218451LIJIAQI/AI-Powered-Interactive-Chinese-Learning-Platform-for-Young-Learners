package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.enums.PackageItemType;

/**
 * Persistence operations for {@link PackageItem} \u2014 the polymorphic
 * join row attaching either a {@code Lesson} or a {@code LearningModule}
 * to a {@code CoursePackage}, with an explicit {@code sortOrder} that
 * the package author controls.
 *
 * <p>Every read eager-fetches {@code coursePackage} +
 * {@code coursePackage.teacher} + {@code coursePackage.teacher.user}
 * so the package detail mapper, the teacher edit panel and the IDOR
 * guard on the package-item mutation flow all hydrate the parent
 * package metadata in a single round trip.</p>
 */
public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {

    /**
     * Returns every item of {@code coursePackageId} in the order the
     * package author intended. Sorts primarily by {@code sortOrder}
     * (the explicit reorderable position the author picks in the editor)
     * with {@code createdAt} as a deterministic tie-breaker so two items
     * sharing the same sort order still render in insertion order
     * rather than in arbitrary database order.
     */
    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    List<PackageItem> findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long coursePackageId);

    /**
     * Paged variant of
     * {@link #findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long)}
     * for endpoints that page through long item lists.
     */
    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    Page<PackageItem> findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long coursePackageId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code PackageItem \u2192 CoursePackage \u2192 TeacherProfile \u2192 User}
     * so a teacher cannot mutate another teacher's package items by
     * guessing their ids.
     */
    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    Optional<PackageItem> findByIdAndCoursePackageTeacherUserId(Long id, Long teacherUserId);

    /**
     * Reverse lookup used by the lesson / module mappers to discover
     * which packages reference the given target item, so the catalogue
     * card can show "Included in N packages" badges and the
     * student-access service can resolve parent-package entitlement.
     */
    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    List<PackageItem> findByItemTypeAndItemId(PackageItemType itemType, Long itemId);

    /**
     * Constant-time package-item membership predicate used by live-learning
     * write paths before attaching package-scoped resources to a lesson or
     * module. Higher-level services decide whether a lesson matches by direct
     * lesson membership, inherited module membership, or both.
     */
    boolean existsByCoursePackageIdAndItemTypeAndItemId(
            Long coursePackageId,
            PackageItemType itemType,
            Long itemId);
}
