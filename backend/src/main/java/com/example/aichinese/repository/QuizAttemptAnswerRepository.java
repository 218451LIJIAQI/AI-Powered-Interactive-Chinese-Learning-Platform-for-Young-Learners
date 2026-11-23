package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizQuestion;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    @Query("""
            select a from QuizAttemptAnswer a left join fetch a.selectedOption
            where a.quizAttempt.id = :attemptId order by a.createdAt, a.id
            """)
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAscIdAsc(@Param("attemptId") Long attemptId);

    Optional<QuizAttemptAnswer> findByQuizAttemptAndQuestion(QuizAttempt attempt, QuizQuestion question);

    interface AnswerCounts {
        int getCorrect();

        int getTotal();
    }

    @Query("""
            select count(case when a.correct = true then 1 end) as correct, count(a) as total
            from QuizAttemptAnswer a where a.quizAttempt.id = :attemptId
            """)
    AnswerCounts countAnswers(@Param("attemptId") Long attemptId);

    /** Derived delete removes managed answers from the L1 cache before the parent attempt is deleted. */
    void deleteByQuizAttempt(QuizAttempt attempt);

    /**
     * Bulk-deletes answers belonging to unsubmitted attempts of a quiz; {@code clearAutomatically} is intentionally
     * omitted so the caller's locked lesson/quiz graph remains managed in the persistence context.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from QuizAttemptAnswer a
            where a.quizAttempt.id in (
                select qa.id from QuizAttempt qa where qa.quiz.id = :quizId and qa.submitted = false
            )
            """)
    void deleteUnsubmittedByQuizId(@Param("quizId") Long quizId);
}
