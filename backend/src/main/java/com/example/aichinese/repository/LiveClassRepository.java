package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link LiveClass} — scheduled real-time
 * teacher-led sessions that can either back a specific {@code Lesson} or
 * a {@code CoursePackage}.
 *
 * <p>Every read on this repository references the
 * {@link LiveClass#GRAPH_DETAIL named entity graph} so the downstream
 * response mapper never regresses into the per-row lazy-load N+1
 * pattern. The graph is centralised on the entity rather than repeated
 * verbatim on each method, eliminating the previous ten-fold duplication
 * of an identical {@code attributePaths} array.</p>
 *
 * <p>Inbound queries that need to discriminate by visibility / status
 * are expressed as JPQL with explicit LEFT JOINs (see
 * {@link #findStudentVisibleClasses(ContentStatus, Collection, Pageable)}
 * for the rationale behind the explicit-join style).</p>
 */
public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    /**
     * Eager-fetch override of the inherited single-row read. Used by
     * {@code LiveLearningAccessService}, {@code NotificationService}
     * and {@code LiveClassService} after a status transition to render
     * the post-transition payload without re-loading the row.
     */
    @Override
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @NonNull
    Optional<LiveClass> findById(@NonNull Long id);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code LiveClass → TeacherProfile → User} so a teacher cannot
     * mutate another teacher's class by guessing its id.
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    /**
     * Pessimistic-write variant for the status-transition flow (cancel /
     * reschedule / start / complete) so two admins or a teacher and an
     * admin cannot race the same lifecycle change. The lock pairs with
     * the entity's {@code @Version} field as a defence-in-depth: the
     * pessimistic lock serialises in-flight writers, while the optimistic
     * version still catches stale reads that bypassed this method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query("select lc from LiveClass lc where lc.id = :id")
    Optional<LiveClass> findLockedById(@Param("id") Long id);

    /**
     * Teacher-owned live class list for the analytics service's
     * per-teacher snapshot. Backed by
     * {@code idx_live_classes_teacher_id}.
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    List<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId);

    /**
     * Paged teacher-owned live class list for the teacher dashboard.
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Page<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId, Pageable pageable);

    /**
     * Admin schedule view — returns every live class ordered by start
     * time so the database does the sorting (using
     * {@code idx_live_classes_scheduled_start_at}) instead of pulling
     * the entire table into memory and sorting in Java.
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Page<LiveClass> findAllByOrderByScheduledStartAtAsc(Pageable pageable);

    /**
     * Returns every live class that targets the given lesson directly,
     * or targets one of the supplied related package ids. Used by the
     * student lesson detail view to surface upcoming/ongoing live
     * sessions without scanning the entire live class table.
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query("""
            select lc from LiveClass lc
            where lc.lesson.id = :lessonId
               or lc.coursePackage.id in :packageIds
            """)
    List<LiveClass> findRelatedToLessonOrPackages(
            @Param("lessonId") Long lessonId,
            @Param("packageIds") Collection<Long> packageIds);

    /**
     * Student-visible catalogue of live classes ordered by start time.
     * A class is visible when it links to either a published lesson or
     * a published package (mirrors the previous in-memory
     * {@code isStudentVisibleClass} filter). Pushing the visibility
     * predicate down to SQL keeps this student-facing endpoint
     * O(visible classes) instead of scanning the full live class table
     * every time a student opens the list.
     *
     * <p>Uses explicit LEFT JOINs rather than property-navigation
     * ({@code lc.lesson.contentStatus}) because Hibernate generates
     * implicit INNER JOINs for nested property paths in JPQL even when
     * guarded by an {@code is null} OR-branch — which silently filtered
     * out every row that had a non-null lesson FK when we first tried
     * the simpler form.</p>
     */
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query(value = """
            select lc from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
              and lc.liveClassStatus in :visibleStatuses
            order by lc.scheduledStartAt asc
            """, countQuery = """
            select count(lc) from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
              and lc.liveClassStatus in :visibleStatuses
            """)
    Page<LiveClass> findStudentVisibleClasses(
            @Param("published") ContentStatus published,
            @Param("visibleStatuses") Collection<LiveClassStatus> visibleStatuses,
            Pageable pageable);
}
