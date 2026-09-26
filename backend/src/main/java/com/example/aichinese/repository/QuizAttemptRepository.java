package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.repository.projection.QuizAttemptAnalyticsRow;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    @Query("""
            select attempt.id as id, attempt.student.id as studentId,
                   lesson.id as lessonId, lesson.accessType as lessonAccessType,
                   module.id as moduleId, attempt.score as score,
                   attempt.maxScore as maxScore, attempt.submitted as submitted,
                   attempt.completedAt as completedAt, attempt.createdAt as createdAt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
            left join lesson.module module
            where attempt.student.id = :studentId and attempt.id > :afterId
            order by attempt.id asc
            """)
    List<QuizAttemptAnalyticsRow> findStudentAnalyticsBatch(
            @Param("studentId") Long studentId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    @Query("""
            select attempt.id as id, attempt.student.id as studentId,
                   lesson.id as lessonId, lesson.accessType as lessonAccessType,
                   module.id as moduleId, attempt.score as score,
                   attempt.maxScore as maxScore, attempt.submitted as submitted,
                   attempt.completedAt as completedAt, attempt.createdAt as createdAt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
            left join lesson.module module
            where quiz.teacher.id = :teacherId and attempt.id > :afterId
            order by attempt.id asc
            """)
    List<QuizAttemptAnalyticsRow> findTeacherAnalyticsBatch(
            @Param("teacherId") Long teacherId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    @Query("""
            select attempt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
            left join lesson.module module
            where attempt.student.id = :studentId
              and (lesson.id is null or lesson.accessType <> :paidAccess
                   or exists (select entitlement.id from PaymentEntitlement entitlement
                              where entitlement.student.id = attempt.student.id
                                and entitlement.active = true
                                and ((entitlement.itemType = 'LESSON' and entitlement.itemId = lesson.id)
                                     or (entitlement.itemType = 'MODULE' and entitlement.itemId = module.id))))
            order by attempt.completedAt desc, attempt.createdAt desc
            """)
    List<QuizAttempt> findRecentAccessibleForStudent(
            @Param("studentId") Long studentId, @Param("paidAccess") AccessType paidAccess, Pageable pageable);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    @Query("""
            select attempt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
            left join lesson.module module
            where quiz.teacher.id = :teacherId
              and (lesson.id is null or lesson.accessType <> :paidAccess
                   or exists (select entitlement.id from PaymentEntitlement entitlement
                              where entitlement.student.id = attempt.student.id
                                and entitlement.active = true
                                and ((entitlement.itemType = 'LESSON' and entitlement.itemId = lesson.id)
                                     or (entitlement.itemType = 'MODULE' and entitlement.itemId = module.id))))
            order by attempt.completedAt desc, attempt.createdAt desc
            """)
    List<QuizAttempt> findRecentAccessibleForTeacher(
            @Param("teacherId") Long teacherId, @Param("paidAccess") AccessType paidAccess, Pageable pageable);

    List<QuizAttempt> findByQuizTeacherIdOrderByCompletedAtDescCreatedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user", "student.currentLevel" })
    List<QuizAttempt> findByQuizTeacherIdAndStudentIdInOrderByCompletedAtDescCreatedAtDesc(
            Long teacherId,
            List<Long> studentIds);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(Long quizId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndStudentUserId(Long id, Long studentUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByStudentIdOrderByCompletedAtDescCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndQuizTeacherUserId(Long studentId, Long teacherUserId);

    boolean existsByStudentIdAndQuizLessonIdAndQuizTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);

    boolean existsByQuizIdAndSubmittedTrue(Long quizId);
}
