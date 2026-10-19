package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.WrongAnswerCountProjection;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    @Query("""
            select question.questionType as questionType, count(answer) as answerCount
            from QuizAttemptAnswer answer
            join answer.quizAttempt attempt
            join answer.question question
            where attempt.student.id = :studentId
              and answer.correct = false
            group by question.questionType
            order by max(answer.createdAt) desc
            """)
    List<WrongAnswerCountProjection> countWrongAnswersByQuestionType(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAsc(Long quizAttemptId);

    /**
     * Titles of the lessons the student answered wrongly, one row per lesson, the lesson with the most
     * recent wrong answer first (progress weak areas). Grouped by lesson id so the database collation
     * never merges two different titles; callers strip, skip blanks and de-duplicate.
     */
    @Query("""
            select lesson.title
            from QuizAttemptAnswer answer
            join answer.quizAttempt attempt
            join answer.question question
            join question.quiz quiz
            join quiz.lesson lesson
            where attempt.student.id = :studentId
              and answer.correct = false
            group by lesson.id, lesson.title
            order by max(answer.createdAt) desc, lesson.id desc
            """)
    List<String> findRecentWrongAnswerLessonTitles(@Param("studentId") Long studentId, Pageable pageable);

    /** Wrong answers of the given students on one teacher's quizzes (roster weak areas). */
    @EntityGraph(attributePaths = { "question", "question.quiz", "question.quiz.lesson", "quizAttempt", "quizAttempt.student" })
    List<QuizAttemptAnswer> findByQuizAttemptQuizTeacherIdAndQuizAttemptStudentIdInAndCorrectFalseOrderByCreatedAtDesc(
            Long teacherId,
            List<Long> studentIds);

    /** Number of answers in an attempt where the student revealed the hint (teacher insight, D8). */
    long countByQuizAttemptIdAndHintUsedTrue(Long quizAttemptId);

    @EntityGraph(attributePaths = { "question", "question.quiz", "question.quiz.lesson", "question.quiz.lesson.module", "quizAttempt", "quizAttempt.quiz" })
    @Query(
            value = """
                    select answer from QuizAttemptAnswer answer
                    join answer.quizAttempt attempt
                    join attempt.quiz quiz
                    join quiz.lesson lesson
                    where attempt.student.id = :studentId
                      and answer.correct = false
                      and lesson.contentStatus = :publishedStatus
                      and quiz.contentStatus <> :archivedStatus
                    order by answer.createdAt desc
                    """,
            countQuery = """
                    select count(answer) from QuizAttemptAnswer answer
                    join answer.quizAttempt attempt
                    join attempt.quiz quiz
                    join quiz.lesson lesson
                    where attempt.student.id = :studentId
                      and answer.correct = false
                      and lesson.contentStatus = :publishedStatus
                      and quiz.contentStatus <> :archivedStatus
                    """)
    Page<QuizAttemptAnswer> findAccessibleWrongAnswers(
            @Param("studentId") Long studentId,
            @Param("publishedStatus") ContentStatus publishedStatus,
            @Param("archivedStatus") ContentStatus archivedStatus,
            Pageable pageable);
}
