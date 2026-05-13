package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RecordedLessonVideo;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    @Override
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findAll();

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonIdAndActiveTrue(Long id, Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndActiveTrue(Long id);

    /**
     * Teacher schedule view — returns every active recorded video that the
     * teacher owns. Filtering at the database level avoids the previous pattern
     * of loading every recorded video in the system before discarding rows
     * belonging to other teachers.
     */
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findByActiveTrueAndLessonTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId);

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
}
