package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

/**
 * Persistence operations for {@link CoursePackage} — bundles of one or
 * more {@code Lesson}s (and optionally {@code LearningModule}s) sold as
 * a single purchasable artefact.
 *
 * <p>Every read references the {@link CoursePackage#GRAPH_DETAIL named
 * entity graph} so the discovery, teacher-dashboard and admin moderation
 * mappers all hydrate the same teacher / teacher.user / level / category
 * attribute set in a single SQL round trip. Centralising the graph on
 * the entity (rather than repeating an eight-fold {@code attributePaths}
 * array here) prevents the repository and the relational shape from
 * drifting whenever a new related field is added.</p>
 *
 * <p>Visibility-gated overloads
 * ({@code findByIdAndContentStatusAndTeacher...}) compose the
 * package-status, teacher-status and public-visibility checks at the
 * database layer so a single round-trip resolves "may this student see
 * this package" without an extra service-layer fan-out.</p>
 */
public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long>, JpaSpecificationExecutor<CoursePackage> {

    /**
     * Teacher-owned package list for the analytics service's per-teacher
     * snapshot. Backed by {@code idx_course_packages_teacher_id}.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * Paged teacher-owned package list for the teacher dashboard.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Page<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code CoursePackage → TeacherProfile → User} so a teacher
     * cannot mutate another teacher's package by guessing its id.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Eager-fetch override of {@link JpaRepository#findById(Object)}
     * used by admin moderation flows and integration tests that need
     * the full hydrated row.
     */
    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    Optional<CoursePackage> findById(@NonNull Long id);

    /**
     * Status-gated lookup used by the purchase / entitlement flow so
     * archived or draft packages cannot be added to a cart even if the
     * id is otherwise known to the caller.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    /**
     * Public-catalogue read: returns the package only when it is
     * {@code PUBLISHED}, owned by an {@code APPROVED} teacher whose
     * profile is publicly visible. Composing all three predicates in a
     * single derived method keeps the visibility decision in SQL and
     * avoids leaking the existence of suppressed-but-published packages
     * to anonymous catalogue requests.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    /**
     * Bulk hydration of multiple packages by id, used by the
     * package-aware variants of the catalogue listing flow. The caller
     * is expected to pass a non-empty list — Spring Data's JPQL
     * {@code IN} translation handles the empty case as {@code 1 = 0}
     * but downstream code paths still iterate a (necessarily empty)
     * result, so it is cheaper to short-circuit at the service layer.
     */
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByIdIn(List<Long> ids);

    /**
     * Counts published / draft / archived packages for a single
     * teacher; backs the public teacher profile's "course count" tile
     * and the teacher dashboard's status histogram.
     */
    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    /**
     * Specification-based admin content search used by
     * {@code AdminContentReviewService}. The named entity graph keeps
     * the response mapper from triggering one lazy SELECT per row when
     * assembling moderation submissions.
     *
     * <p>The {@link Specification} parameter is permitted to be
     * {@code null} ("no extra filters"); the admin service composes
     * status / teacher / keyword predicates and only passes
     * {@code null} from internal callers.</p>
     */
    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    List<CoursePackage> findAll(@Nullable Specification<CoursePackage> specification);

    /**
     * Counts published / draft / archived packages platform-wide; backs
     * the admin dashboard's content-published tile.
     */
    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * breakdown. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()}: pushes
     * the {@code GROUP BY} into MySQL using the
     * {@code idx_course_packages_status} index instead of materialising
     * the full table client-side.
     */
    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
