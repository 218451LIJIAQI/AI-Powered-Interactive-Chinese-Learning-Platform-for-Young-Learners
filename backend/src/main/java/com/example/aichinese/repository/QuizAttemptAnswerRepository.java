package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.WrongAnswerCountProjection;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    @Query("""
            select question.questionType as questionType, count(answer) as answerCount
            from QuizAttemptAnswer answer
            join answer.quizAttempt attempt
            join answer.question question
            join question.quiz quiz
            join quiz.lesson lesson
            where attempt.student.id = :studentId
              and answer.correct = false
              and (
                  lesson.accessType <> :paidAccessType
                  or exists (
                      select entitlement.id from PaymentEntitlement entitlement
                      where entitlement.student.id = :studentId
                        and entitlement.active = true
                        and entitlement.itemType = 'LESSON'
                        and entitlement.itemId = lesson.id
                  )
                  or exists (
                      select moduleEntitlement.id from PaymentEntitlement moduleEntitlement
                      where moduleEntitlement.student.id = :studentId
                        and moduleEntitlement.active = true
                        and moduleEntitlement.itemType = 'MODULE'
                        and moduleEntitlement.itemId = lesson.module.id
                  )
              )
            group by question.questionType
            order by max(answer.createdAt) desc
            """)
    List<WrongAnswerCountProjection> countAccessibleWrongAnswersByQuestionType(
            @Param("studentId") Long studentId,
            @Param("paidAccessType") AccessType paidAccessType);

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAsc(Long quizAttemptId);

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt", "quizAttempt.quiz" })
    List<QuizAttemptAnswer> findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(Long studentId);

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
                      and (
                          lesson.accessType = :freeAccessType
                          or exists (
                              select entitlement.id from PaymentEntitlement entitlement
                              where entitlement.student.id = :studentId
                                and entitlement.active = true
                                and entitlement.itemType = 'LESSON'
                                and entitlement.itemId = lesson.id
                          )
                          or (
                              lesson.module is not null
                              and exists (
                                  select moduleEntitlement.id from PaymentEntitlement moduleEntitlement
                                  where moduleEntitlement.student.id = :studentId
                                    and moduleEntitlement.active = true
                                    and moduleEntitlement.itemType = 'MODULE'
                                    and moduleEntitlement.itemId = lesson.module.id
                              )
                          )
                      )
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
                      and (
                          lesson.accessType = :freeAccessType
                          or exists (
                              select entitlement.id from PaymentEntitlement entitlement
                              where entitlement.student.id = :studentId
                                and entitlement.active = true
                                and entitlement.itemType = 'LESSON'
                                and entitlement.itemId = lesson.id
                          )
                          or (
                              lesson.module is not null
                              and exists (
                                  select moduleEntitlement.id from PaymentEntitlement moduleEntitlement
                                  where moduleEntitlement.student.id = :studentId
                                    and moduleEntitlement.active = true
                                    and moduleEntitlement.itemType = 'MODULE'
                                    and moduleEntitlement.itemId = lesson.module.id
                              )
                          )
                      )
                    """)
    Page<QuizAttemptAnswer> findAccessibleWrongAnswers(
            @Param("studentId") Long studentId,
            @Param("publishedStatus") ContentStatus publishedStatus,
            @Param("archivedStatus") ContentStatus archivedStatus,
            @Param("freeAccessType") AccessType freeAccessType,
            Pageable pageable);
}
