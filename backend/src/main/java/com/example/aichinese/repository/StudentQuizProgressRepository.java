package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentQuizProgress;

/**
 * Persistence operations for {@link StudentQuizProgress} \u2014 the
 * per-student per-quiz progress aggregate that records best-score,
 * attempt-count and last-attempt-at, complementing {@code QuizAttempt}
 * (which holds individual attempt rows). Each read eager-fetches
 * {@code lesson} so the lesson-detail mapper can render the parent
 * lesson title in one round trip.
 */
public interface StudentQuizProgressRepository extends JpaRepository<StudentQuizProgress, Long> {

    /**
     * Returns every quiz-progress row a learner has for a given lesson
     * (one per quiz authored on that lesson) ordered by recency. Used
     * by the lesson-detail page to surface "your best score per quiz".
     */
    @EntityGraph(attributePaths = { "lesson" })
    List<StudentQuizProgress> findByStudentIdAndLessonIdOrderByUpdatedAtDesc(Long studentId, Long lessonId);

    /**
     * Single-row lookup of a learner's progress on a specific quiz.
     * Backed by the unique constraint on
     * {@code (student_id, quiz_id)} which guarantees at most one row
     * per pair.
     */
    @EntityGraph(attributePaths = { "lesson" })
    Optional<StudentQuizProgress> findByStudentIdAndQuizId(Long studentId, Long quizId);
}
