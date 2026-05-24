package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Persistence operations for {@link RecordedLessonVideo} — pre-recorded
 * video resources attached to a {@code Lesson} (and optionally a
 * {@code CoursePackage}).
 *
 * <p>Every read references the
 * {@link RecordedLessonVideo#GRAPH_DETAIL named entity graph} so the
 * downstream response mapper hydrates lesson / lesson.teacher /
 * lesson.teacher.user / coursePackage / uploadedFile in a single round
 * trip without the per-row lazy-load N+1 pattern. Centralising the
 * graph on the entity eliminated the previous eight-fold duplication
 * of an identical {@code attributePaths} array.</p>
 *
 * <p>{@code active} acts as a soft-delete flag — teacher-removed
 * videos remain in the table for the {@code VideoProgress} audit
 * trail but are excluded from every student-visible read here.</p>
 */
public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    /**
     * Paged teacher video list for a lesson, excluding soft-deleted
     * rows. Used by the teacher recorded-content editor; the named
     * eager-fetch keeps the response mapper from triggering one lazy
     * SELECT per row.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Page<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId, Pageable pageable);

    /**
     * Lesson-scoped IDOR-safe single-row lookup: returns the video only
     * when it belongs to {@code lessonId} and has not been soft-deleted.
     * Used by the student playback flow after the surrounding lesson
     * access check has already passed.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndLessonIdAndActiveTrue(Long id, Long lessonId);

    /**
     * Teacher IDOR-safe single-row lookup that traverses
     * {@code RecordedLessonVideo → Lesson → TeacherProfile → User}
     * so a teacher cannot mutate another teacher's video by guessing
     * its id. Returns soft-deleted rows too so the teacher edit panel
     * can restore them.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    /**
     * Active-only single-row lookup used by the notification access
     * check ({@code NotificationService}) and the live-learning access
     * service when verifying that a referenced video has not since
     * been soft-deleted.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndActiveTrue(Long id);

    /**
     * Returns every active recorded video that targets the given lesson
     * directly, or targets one of the supplied related package ids.
     * Used by the student lesson detail view to surface recorded
     * sessions without scanning the entire recorded video table.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    @Query("""
            select v from RecordedLessonVideo v
            where v.active = true and (
                v.lesson.id = :lessonId
                or v.coursePackage.id in :packageIds
            )
            """)
    List<RecordedLessonVideo> findActiveRelatedToLessonOrPackages(
            @Param("lessonId") Long lessonId,
            @Param("packageIds") Collection<Long> packageIds);

    /**
     * Student-visible, paywall-aware fetch of recorded videos for a
     * lesson. Excludes locked videos in PAID packages that the supplied
     * student has not purchased, eliminating an in-memory entitlement
     * check that previously ran per-row inside the lesson-detail
     * mapper.
     */
    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    @Query(value = """
            select distinct v from RecordedLessonVideo v
            left join v.coursePackage p
            left join PaymentEntitlement e
              on e.student.id = :studentId
             and e.itemType = 'PACKAGE'
             and e.itemId = p.id
             and e.active = true
            where v.active = true
              and v.lesson.id = :lessonId
              and (p is null or p.contentStatus = :published)
              and (p is null or p.accessType <> :paid or e.id is not null)
            order by v.sortOrder asc, v.createdAt asc
            """, countQuery = """
            select count(distinct v.id) from RecordedLessonVideo v
            left join v.coursePackage p
            left join PaymentEntitlement e
              on e.student.id = :studentId
             and e.itemType = 'PACKAGE'
             and e.itemId = p.id
             and e.active = true
            where v.active = true
              and v.lesson.id = :lessonId
              and (p is null or p.contentStatus = :published)
              and (p is null or p.accessType <> :paid or e.id is not null)
            """)
    Page<RecordedLessonVideo> findAccessibleByLessonIdForStudent(
            @Param("lessonId") Long lessonId,
            @Param("studentId") Long studentId,
            @Param("published") ContentStatus published,
            @Param("paid") AccessType paid,
            Pageable pageable);
}
