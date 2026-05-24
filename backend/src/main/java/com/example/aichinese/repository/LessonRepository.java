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

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

/**
 * Persistence operations for {@link Lesson} — the central teaching unit
 * authored by teachers, optionally grouped under a
 * {@link com.example.aichinese.entity.LearningModule} and surfaced via
 * the discovery catalog when {@code contentStatus = PUBLISHED}.
 *
 * <p>Read methods that feed the discovery / catalog mappers
 * consistently eager-fetch {@code teacher} + {@code teacher.user} +
 * {@code module} + {@code level} + {@code category} so the response
 * mapper never falls back to per-row lazy SELECTs. The catalog
 * {@link #findAll(Specification)} override exists for exactly the same
 * reason: without it, the {@code JpaSpecificationExecutor} default
 * would lazy-load these associations and recreate the N+1 pattern we
 * spent care to eliminate.</p>
 */
public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Page<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    @NonNull
    Optional<Lesson> findById(@NonNull Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByIdIn(List<Long> ids);

    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByModuleIdAndContentStatus(Long moduleId, ContentStatus contentStatus);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    @NonNull
    Page<Lesson> findAll(@Nullable Specification<Lesson> specification, @NonNull Pageable pageable);

    /**
     * Override of the {@link JpaSpecificationExecutor} {@code findAll(Specification)}
     * method so that the public catalog discovery query eagerly fetches the
     * relationships referenced by the response mapper. Without this override
     * the mapper would trigger one lazy SELECT per lesson for
     * teacher/category/level, recreating the N+1 problem we set out to
     * eliminate.
     */
    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    @NonNull
    List<Lesson> findAll(@Nullable Specification<Lesson> specification);

    long countByTeacherId(Long teacherId);

    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * dashboard. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()} — replaces a
     * full-table load with a single SQL {@code GROUP BY}.
     */
    @Query("select l.contentStatus as status, count(l) as count from Lesson l group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
