package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizQuestion;

/**
 * Persistence operations for {@link QuizQuestion} \u2014 the individual
 * question rows that make up a {@code Quiz} (the parent aggregate).
 * Each read eager-fetches {@code quiz} + {@code quiz.lesson} +
 * {@code quiz.teacher} + {@code quiz.teacher.user} so the teacher
 * authoring panel and the student attempt page can render the
 * question's parent context (quiz title, lesson, author) in a single
 * round trip without N+1 lazy lookups.
 */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /**
     * Returns every question of {@code quizId} in the order the author
     * intended. Sorts primarily by {@code sortOrder} with
     * {@code createdAt} as a deterministic tie-breaker so two
     * questions sharing the same sort order still render in insertion
     * order rather than in arbitrary database order.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user" })
    List<QuizQuestion> findByQuizIdOrderBySortOrderAscCreatedAtAsc(Long quizId);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code QuizQuestion \u2192 Quiz \u2192 TeacherProfile \u2192 User}
     * so a teacher cannot mutate another teacher's question by guessing
     * its id.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user" })
    Optional<QuizQuestion> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);
}
