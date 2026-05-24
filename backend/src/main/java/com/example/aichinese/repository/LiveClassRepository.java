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
 * <p>Every read on this repository uses the same {@link EntityGraph} of
 * teacher / lesson / course-package + their respective teachers so the
 * downstream response mapper never regresses into the per-row lazy-load
 * N+1 pattern. The graph is duplicated on each method (rather than
 * declared once on a base class) because Spring Data resolves
 * {@code @EntityGraph} per-method, not per-interface.</p>
 *
 * <p>Inbound queries that need to discriminate by visibility / status
 * are expressed as JPQL with explicit LEFT JOINs (see
 * {@link #findStudentVisibleClasses(ContentStatus, Collection)} for the
 * rationale behind the explicit-join style).</p>
 */
public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @NonNull
    List<LiveClass> findAll();

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @NonNull
    Optional<LiveClass> findById(@NonNull Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @Query("select lc from LiveClass lc where lc.id = :id")
    Optional<LiveClass> findLockedById(@Param("id") Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Page<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId, Pageable pageable);

    /**
     * Admin schedule view — returns every live class ordered by start time so
     * the database does the sorting (using the implicit index on the column)
     * instead of pulling the entire table into memory and sorting in Java.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findAllByOrderByScheduledStartAtAsc();

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Page<LiveClass> findAllByOrderByScheduledStartAtAsc(Pageable pageable);

    /**
     * Returns every live class that targets the given lesson directly, or
     * targets one of the supplied related package ids. Used by the student
     * lesson detail view to surface upcoming/ongoing live sessions without
     * scanning the entire live class table.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @Query("""
            select lc from LiveClass lc
            where lc.lesson.id = :lessonId
               or lc.coursePackage.id in :packageIds
            """)
    List<LiveClass> findRelatedToLessonOrPackages(
            @Param("lessonId") Long lessonId,
            @Param("packageIds") Collection<Long> packageIds);

    /**
     * Student-visible catalog of live classes ordered by start time. A class
     * is visible when it links to either a published lesson or a published
     * package (mirrors the previous in-memory {@code isStudentVisibleClass}
     * filter). Pushing the visibility predicate down to SQL keeps this
     * student-facing endpoint O(visible classes) instead of scanning the
     * full live class table every time a student opens the list.
     *
     * <p>Uses explicit LEFT JOINs rather than property-navigation
     * ({@code lc.lesson.contentStatus}) because Hibernate generates implicit
     * INNER JOINs for nested property paths in JPQL even when guarded by an
     * {@code is null} OR-branch — which silently filtered out every row that
     * had a non-null lesson FK when we first tried the simpler form.</p>
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @Query("""
            select lc from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
              and lc.liveClassStatus in :visibleStatuses
            order by lc.scheduledStartAt asc
            """)
    List<LiveClass> findStudentVisibleClasses(
            @Param("published") ContentStatus published,
            @Param("visibleStatuses") Collection<LiveClassStatus> visibleStatuses);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
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
