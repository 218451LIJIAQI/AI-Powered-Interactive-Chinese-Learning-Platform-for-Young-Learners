package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    @Override
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    @NonNull
    List<RecordedLessonVideo> findAll();

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Page<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId, Pageable pageable);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonIdAndActiveTrue(Long id, Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndActiveTrue(Long id);

    /**
     * Returns every active recorded video that targets the given lesson directly,
     * or targets one of the supplied related package ids. Used by the student
     * lesson detail view to surface recorded sessions without scanning the
     * entire recorded video table.
     */
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    @org.springframework.data.jpa.repository.Query("""
            select v from RecordedLessonVideo v
            where v.active = true and (
                v.lesson.id = :lessonId
                or v.coursePackage.id in :packageIds
            )
            """)
    List<RecordedLessonVideo> findActiveRelatedToLessonOrPackages(
            @org.springframework.data.repository.query.Param("lessonId") Long lessonId,
            @org.springframework.data.repository.query.Param("packageIds") java.util.Collection<Long> packageIds);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    @org.springframework.data.jpa.repository.Query(value = """
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
            @org.springframework.data.repository.query.Param("lessonId") Long lessonId,
            @org.springframework.data.repository.query.Param("studentId") Long studentId,
            @org.springframework.data.repository.query.Param("published") ContentStatus published,
            @org.springframework.data.repository.query.Param("paid") AccessType paid,
            Pageable pageable);
}
