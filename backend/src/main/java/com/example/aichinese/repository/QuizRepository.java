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

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Page<Quiz> findByLessonIdAndContentStatusNotOrderByCreatedAtAsc(Long lessonId, com.example.aichinese.entity.enums.ContentStatus contentStatus, Pageable pageable);

    long countByLessonId(Long lessonId);

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
