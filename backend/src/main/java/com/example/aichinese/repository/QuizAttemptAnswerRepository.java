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

/**
 * Persistence operations for {@link QuizAttemptAnswer} \u2014 the single
 * answer row a learner submitted for one quiz question inside one
 * attempt, capturing which option they selected and whether it was
 * correct.
 *
 * <p>The repository exposes three reads that all eager-fetch the
 * parent {@code question} (and its {@code quiz}) so the response
 * mapper can render the question text + correctness without N+1
 * lookups. The flagship read is {@link #findAccessibleWrongAnswers}
 * which filters by lesson visibility AND entitlement: the
 * "weak areas" engine must never surface a hint that references a
 * lesson the learner has lost access to (e.g. after a refund) or that
 * was archived since the attempt was made.</p>
 */
public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    /**
     * Returns every answer for a single attempt in submission order \u2014
     * used by the post-submit results panel and the quiz integration
     * tests. Backed by the {@code quiz_attempt_id} foreign-key index.
     */
    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAsc(Long quizAttemptId);

    /**
     * Returns every wrong answer for a learner across every attempt \u2014
     * used by the weak-areas extraction in {@code DashboardService},
     * {@code StudentLearningProgressService} and
     * {@code AnalyticsService}. Service-side filtering removes answers
     * pointing at lessons the learner has lost access to; the
     * {@link #findAccessibleWrongAnswers} variant pushes that filter
     * down into SQL for the paged endpoint.
     */
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
