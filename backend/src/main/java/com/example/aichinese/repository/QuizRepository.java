package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Persistence operations for {@link Quiz} \u2014 the assessment header
 * attached to a {@code Lesson}, parent to a list of {@code QuizQuestion}s.
 *
 * <p>Each read eager-fetches the parent lesson and teacher chain so the
 * teacher quiz editor and the student attempt page can render the quiz
 * with its parent context in a single round trip. The flagship read is
 * {@link #findAccessiblePublishedForStudent} which pushes the lesson
 * visibility AND entitlement check down into SQL: the student quiz
 * listing must never surface a quiz attached to a lesson the learner
 * has lost access to (e.g. after a refund) or that was archived.</p>
 */
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * Paged teacher quiz list for a given lesson, excluding the supplied
     * status (typically {@code ARCHIVED} so the editor shows draft +
     * published variants).
     */
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Page<Quiz> findByLessonIdAndContentStatusNotOrderByCreatedAtAsc(Long lessonId, ContentStatus contentStatus, Pageable pageable);

    /**
     * Counts every quiz attached to a lesson regardless of status \u2014
     * used by the teacher dashboard and the lesson detail tile.
     */
    long countByLessonId(Long lessonId);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code Quiz \u2192 TeacherProfile \u2192 User} so a teacher cannot
     * mutate another teacher's quiz by guessing its id.
     */
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Optional<Quiz> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "lesson", "lesson.module", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    @Query("""
            select distinct q
            from Quiz q
            join q.lesson lesson
            where q.contentStatus = :quizStatus
              and lesson.contentStatus = :lessonStatus
              and (:lessonId is null or lesson.id = :lessonId)
              and (
                lesson.accessType = com.example.aichinese.entity.enums.AccessType.FREE
                or exists (
                    select entitlement.id
                    from PaymentEntitlement entitlement
                    where entitlement.student.id = :studentId
                      and entitlement.active = true
                      and entitlement.itemType = 'LESSON'
                      and entitlement.itemId = lesson.id
                )
                or (
                    lesson.module is not null
                    and exists (
                        select moduleEntitlement.id
                        from PaymentEntitlement moduleEntitlement
                        where moduleEntitlement.student.id = :studentId
                          and moduleEntitlement.active = true
                          and moduleEntitlement.itemType = 'MODULE'
                          and moduleEntitlement.itemId = lesson.module.id
                    )
                )
              )
            order by q.updatedAt desc, q.createdAt desc
            """)
    Page<Quiz> findAccessiblePublishedForStudent(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId,
            @Param("quizStatus") ContentStatus quizStatus,
            @Param("lessonStatus") ContentStatus lessonStatus,
            Pageable pageable);
}
