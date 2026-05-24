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
 * <p>Every read method references the {@link LearningModule#GRAPH_DETAIL
 * named entity graph} so the discovery, teacher-dashboard and admin
 * moderation mappers all hydrate teacher / teacher.user / level /
 * category in a single SQL round trip. Centralising the graph on the
 * entity (rather than repeating an attribute-path array on each method)
 * keeps the repository and the relational shape from drifting whenever
 * a new related field is added.</p>
 *
 * <p>Module-level entitlements are checked by
 * {@code PaymentEntitlement.itemType = 'MODULE'}, which is why
 * accessibility queries appear on the {@link LessonRepository} layer
 * rather than here.</p>
 */
public interface LearningModuleRepository extends JpaRepository<LearningModule, Long>, JpaSpecificationExecutor<LearningModule> {

    /**
     * Teacher-owned module list for the analytics service's per-teacher
     * snapshot. Backed by {@code idx_learning_modules_teacher_id}.
     */
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    List<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * Paged teacher-owned module list for the teacher dashboard.
     */
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Page<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code LearningModule → TeacherProfile → User} so a teacher
     * cannot mutate another teacher's module by guessing its id.
     */
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Optional<LearningModule> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Eager-fetch override of {@link JpaRepository#findById(Object)}
     * used by admin moderation flows and the integration tests that
     * assert hydrated module rows.
     */
    @Override
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    @NonNull
    Optional<LearningModule> findById(@NonNull Long id);

    /**
     * Status-gated lookup used by the purchase / entitlement flow so
     * archived or draft modules cannot be added to a cart even if the
     * id is otherwise known to the caller.
     */
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Optional<LearningModule> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    /**
     * Specification-based admin content search used by
     * {@code AdminContentReviewService}. The named entity graph keeps
     * the response mapper from triggering one lazy SELECT per row when
     * assembling moderation submissions — without this override the
     * inherited {@link JpaSpecificationExecutor#findAll(Specification)}
     * would lose the eager fetch and re-introduce N+1 fan-out on the
     * teacher / level / category relationships.
     *
     * <p>The {@link Specification} parameter is permitted to be
     * {@code null} ("no extra filters"); the admin service composes
     * status / teacher / keyword predicates and only passes
     * {@code null} from internal callers.</p>
     */
    @Override
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    @NonNull
    List<LearningModule> findAll(@Nullable Specification<LearningModule> specification);

    /**
     * Counts modules owned by a single teacher; backs the public
     * teacher profile's "course count" tile and the teacher dashboard's
     * status histogram.
     */
    long countByTeacherId(Long teacherId);

    /**
     * Counts modules in a given content status platform-wide; backs the
     * admin dashboard's content-published tile.
     */
    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * breakdown. Lets the admin breakdown query the database for module
     * status counts directly instead of loading every module into JVM
     * memory to count them; backed by
     * {@code idx_learning_modules_status} which has very low cardinality
     * but still avoids a full table read.
     */
    @Query("select m.contentStatus as status, count(m) as count from LearningModule m group by m.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
