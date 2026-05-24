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
 * the discovery catalogue when {@code contentStatus = PUBLISHED}.
 *
 * <p>Every read method references the {@link Lesson#GRAPH_DETAIL named
 * entity graph} so the discovery, teacher-dashboard and admin moderation
 * mappers all hydrate teacher / teacher.user / module / level /
 * category in a single SQL round trip. Centralising the graph on the
 * entity (rather than repeating the attribute-path array on each
 * method) keeps the repository and the relational shape from drifting
 * whenever a new related field is added.</p>
 *
 * <p>The catalog {@link #findAll(Specification)} override exists for
 * exactly the same reason: without it, the
 * {@link JpaSpecificationExecutor} default would lazy-load these
 * associations and recreate the N+1 pattern we set out to eliminate.</p>
 */
public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    /**
     * Teacher-owned lesson list for the analytics service's per-teacher
     * snapshot. Backed by {@code idx_lessons_teacher_id}.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * Paged teacher-owned lesson list for the teacher dashboard.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Page<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code Lesson → TeacherProfile → User} so a teacher cannot
     * mutate another teacher's lesson by guessing its id.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Eager-fetch override of {@link JpaRepository#findById(Object)}
     * used by admin moderation flows and the integration tests.
     */
    @Override
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    @NonNull
    Optional<Lesson> findById(@NonNull Long id);

    /**
     * Status-gated lookup used by the purchase / entitlement flow so
     * archived or draft lessons cannot be added to a cart even if the
     * id is otherwise known to the caller.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    /**
     * Public-catalogue read: returns the lesson only when it is
     * {@code PUBLISHED}, owned by an {@code APPROVED} teacher whose
     * profile is publicly visible. Composing all three predicates in a
     * single derived method keeps the visibility decision in SQL and
     * avoids leaking the existence of suppressed-but-published lessons
     * to anonymous catalogue requests.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    /**
     * Bulk hydration of multiple lessons by id, used by the catalogue
     * listing flow and the package-content resolution path. Spring
     * Data's JPQL {@code IN} translation handles the empty case
     * gracefully (degrades to {@code 1 = 0}); callers may pre-empt the
     * round trip by checking {@code ids.isEmpty()}.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByIdIn(List<Long> ids);

    /**
     * Counts published / draft / archived lessons for a single teacher;
     * backs the public teacher profile's "course count" tile and the
     * teacher dashboard's status histogram.
     */
    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    /**
     * Lessons that belong to a specific module in a specific status
     * — used by the module-detail page to render only published lessons
     * to learners and all lessons (regardless of status) to the
     * authoring teacher.
     */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByModuleIdAndContentStatus(Long moduleId, ContentStatus contentStatus);

    /**
     * Specification-based admin / catalogue search used by
     * {@code AdminContentReviewService} and the public catalogue. The
     * named entity graph keeps the response mapper from triggering one
     * lazy SELECT per row when assembling moderation submissions or
     * catalogue cards.
     *
     * <p>The {@link Specification} parameter is permitted to be
     * {@code null} ("no extra filters"); callers compose status /
     * teacher / keyword predicates as needed.</p>
     */
    @Override
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    @NonNull
    List<Lesson> findAll(@Nullable Specification<Lesson> specification);

    /**
     * Counts lessons owned by a single teacher; backs the public teacher
     * profile's "course count" tile when summing across statuses.
     */
    long countByTeacherId(Long teacherId);

    /**
     * Counts published / draft / archived lessons platform-wide; backs
     * the admin dashboard's content-published tile.
     */
    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * breakdown. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()} — replaces
     * a full-table load with a single SQL {@code GROUP BY} backed by
     * {@code idx_lessons_status}.
     */
    @Query("select l.contentStatus as status, count(l) as count from Lesson l group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
