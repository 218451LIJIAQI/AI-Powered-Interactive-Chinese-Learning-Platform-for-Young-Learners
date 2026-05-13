package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.enums.ContentStatus;

public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findAll();

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Optional<LiveClass> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId);

    /**
     * Admin schedule view — returns every live class ordered by start time so
     * the database does the sorting (using the implicit index on the column)
     * instead of pulling the entire table into memory and sorting in Java.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findAllByOrderByScheduledStartAtAsc();

    /**
     * Returns every live class that targets the given lesson directly, or
     * targets one of the supplied related package ids. Used by the student
     * lesson detail view to surface upcoming/ongoing live sessions without
     * scanning the entire live class table.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    @org.springframework.data.jpa.repository.Query("""
            select lc from LiveClass lc
            where lc.lesson.id = :lessonId
               or lc.coursePackage.id in :packageIds
            """)
    List<LiveClass> findRelatedToLessonOrPackages(
            @org.springframework.data.repository.query.Param("lessonId") Long lessonId,
            @org.springframework.data.repository.query.Param("packageIds") java.util.Collection<Long> packageIds);

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
    @org.springframework.data.jpa.repository.Query("""
            select lc from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
            order by lc.scheduledStartAt asc
            """)
    List<LiveClass> findStudentVisibleClasses(
            @org.springframework.data.repository.query.Param("published") ContentStatus published);
}
