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

import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

/**
 * Persistence operations for {@link LearningModule} — the optional
 * "unit" wrapper that groups {@link com.example.aichinese.entity.Lesson}
 * rows authored by the same teacher (e.g. "HSK 1 Foundations").
 *
 * <p>Every read method eager-fetches teacher / teacher.user / level /
 * category so the discovery and admin mappers never lazy-load these
 * associations row-by-row. Module-level entitlements are checked by
 * {@code PaymentEntitlement.itemType = 'MODULE'}, which is why
 * accessibility queries appear on the {@link LessonRepository} layer
 * rather than here.</p>
 */
public interface LearningModuleRepository extends JpaRepository<LearningModule, Long>, JpaSpecificationExecutor<LearningModule> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    List<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Page<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<LearningModule> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    Optional<LearningModule> findById(@NonNull Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<LearningModule> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    Page<LearningModule> findAll(@Nullable Specification<LearningModule> specification, @NonNull Pageable pageable);

    long countByTeacherId(Long teacherId);

    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * dashboard. Lets the admin breakdown query the database for module
     * status counts directly instead of loading every module into JVM
     * memory to count them.
     */
    @Query("select m.contentStatus as status, count(m) as count from LearningModule m group by m.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
