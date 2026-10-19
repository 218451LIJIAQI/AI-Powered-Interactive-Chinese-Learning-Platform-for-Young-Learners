package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.repository.projection.QuizAttemptAnalyticsRow;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    @Query("""
            select attempt.id as id, attempt.student.id as studentId,
                   lesson.id as lessonId, attempt.score as score,
                   attempt.maxScore as maxScore, attempt.submitted as submitted,
                   attempt.completedAt as completedAt, attempt.createdAt as createdAt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
            where attempt.student.id = :studentId and attempt.id > :afterId
            order by attempt.id asc
            """)
    List<QuizAttemptAnalyticsRow> findStudentAnalyticsBatch(
            @Param("studentId") Long studentId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    @Query("""
            select attempt.id as id, attempt.student.id as studentId,
                   lesson.id as lessonId, attempt.score as score,
                   attempt.maxScore as maxScore, attempt.submitted as submitted,
                   attempt.completedAt as completedAt, attempt.createdAt as createdAt
            from QuizAttempt attempt
            join attempt.quiz quiz
            left join quiz.lesson lesson
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
            where attempt.student.id = :studentId
            order by attempt.completedAt desc, attempt.createdAt desc
            """)
    List<QuizAttempt> findRecentForStudent(@Param("studentId") Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    @Query("""
            select attempt
            from QuizAttempt attempt
            join attempt.quiz quiz
            where quiz.teacher.id = :teacherId
            order by attempt.completedAt desc, attempt.createdAt desc
            """)
    List<QuizAttempt> findRecentForTeacher(@Param("teacherId") Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user", "student.currentLevel" })
    List<QuizAttempt> findByQuizTeacherIdAndStudentIdInOrderByCompletedAtDescCreatedAtDesc(
            Long teacherId,
            List<Long> studentIds);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Page<QuizAttempt> findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(Long quizId, Pageable pageable);

    /** One student's attempts on the quizzes of one lesson (lesson mastery). */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson" })
    List<QuizAttempt> findByStudentIdAndQuizLessonId(Long studentId, Long lessonId);

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
