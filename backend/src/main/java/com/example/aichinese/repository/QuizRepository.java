package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;

public interface QuizRepository extends JpaRepository<Quiz, Long>, JpaSpecificationExecutor<Quiz> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Page<Quiz> findByLessonIdAndContentStatusNotOrderByCreatedAtAsc(
            Long lessonId,
            ContentStatus contentStatus,
            Pageable pageable);

    long countByLessonIdAndContentStatus(Long lessonId, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Optional<Quiz> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @Override
    @EntityGraph(attributePaths = { "lesson", "lesson.module", "teacher", "teacher.user" })
    @NonNull
    Page<Quiz> findAll(@Nullable Specification<Quiz> specification, @NonNull Pageable pageable);

    /**
     * Quizzes a student may take (ContentVisibilityPolicy): quiz and lesson PUBLISHED, the lesson's
     * module (if any) PUBLISHED, the teacher APPROVED and active. Ordered by learning stage
     * (guided, scaffolded, independent, unset) and then newest first (the previous order).
     */
    @EntityGraph(attributePaths = { "lesson", "lesson.module", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    @Query(value = """
            select q
            from Quiz q
            join q.lesson lesson
            left join lesson.module module
            join lesson.teacher teacher
            join teacher.user teacherUser
            where q.contentStatus = :quizStatus
              and lesson.contentStatus = :lessonStatus
              and (module is null or module.contentStatus = :lessonStatus)
              and teacher.teacherStatus = :approved
              and teacherUser.active = true
              and (:lessonId is null or lesson.id = :lessonId)
            order by case q.learningStage
                         when com.example.aichinese.entity.enums.LearningStage.GUIDED then 0
                         when com.example.aichinese.entity.enums.LearningStage.SCAFFOLDED then 1
                         when com.example.aichinese.entity.enums.LearningStage.INDEPENDENT then 2
                         else 3 end,
                     q.updatedAt desc,
                     q.createdAt desc,
                     q.id desc
            """,
            countQuery = """
            select count(q)
            from Quiz q
            join q.lesson lesson
            left join lesson.module module
            join lesson.teacher teacher
            join teacher.user teacherUser
            where q.contentStatus = :quizStatus
              and lesson.contentStatus = :lessonStatus
              and (module is null or module.contentStatus = :lessonStatus)
              and teacher.teacherStatus = :approved
              and teacherUser.active = true
              and (:lessonId is null or lesson.id = :lessonId)
            """)
    Page<Quiz> findVisibleForStudent(
            @Param("lessonId") Long lessonId,
            @Param("quizStatus") ContentStatus quizStatus,
            @Param("lessonStatus") ContentStatus lessonStatus,
            @Param("approved") TeacherStatus approved,
            Pageable pageable);
}
