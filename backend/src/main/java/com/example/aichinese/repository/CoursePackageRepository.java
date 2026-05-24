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
 * <p>Read methods consistently eager-fetch teacher / teacher.user /
 * level / category to keep the discovery and admin mappers off the
 * lazy-loading hot path. Visibility-gated overloads
 * ({@code findByIdAndContentStatusAndTeacher...}) compose the
 * package-status, teacher-status and public-visibility checks at the
 * database layer so a single round-trip resolves "may this student see
 * this package" without an extra service-layer fan-out.</p>
 */
public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long>, JpaSpecificationExecutor<CoursePackage> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    List<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Page<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    Optional<CoursePackage> findById(@NonNull Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    List<CoursePackage> findByIdIn(List<Long> ids);

    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    Page<CoursePackage> findAll(@Nullable Specification<CoursePackage> specification, @NonNull Pageable pageable);

    /**
     * Specification-based override that eagerly fetches teacher/level/category
     * so that the public catalog mapper does not trigger one lazy SELECT per
     * row when assembling discovery responses.
     */
    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    List<CoursePackage> findAll(@Nullable Specification<CoursePackage> specification);

    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * dashboard. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()}.
     */
    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
