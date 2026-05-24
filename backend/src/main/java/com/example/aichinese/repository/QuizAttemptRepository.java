package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttempt;

/**
 * Persistence operations for {@link QuizAttempt} \u2014 the per-student
 * per-quiz attempt header (one row per attempt, regardless of how many
 * questions the quiz has).
 *
 * <p>Reads come in three shapes tuned to their access pattern:</p>
 * <ul>
 *   <li>Teacher-owned attempts (analytics / class roster): unbounded
 *       lists that the service filters in JVM via accessibility checks
 *       layered on top.</li>
 *   <li>Per-quiz roster (teacher quiz detail): list with the
 *       eager-fetched student so the response mapper can render the
 *       roster in one round trip.</li>
 *   <li>IDOR-safe single-row reads: separate variants for student-
 *       and teacher-scoped lookups so neither role can surface the
 *       other's attempts by guessing an id.</li>
 * </ul>
 *
 * <p>The {@code existsBy*} predicates that traverse
 * {@code quiz.teacher.user} provide the cross-cutting feedback gating
 * mirror of {@code LessonProgressRepository.existsBy*}: a teacher may
 * feedback on a learner whose quiz attempt touches one of the teacher's
 * quizzes, regardless of accompanying lesson progress.</p>
 */
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * Teacher-owned attempts list for the analytics service. Sort
     * sequence ({@code completed_at} desc, {@code created_at} desc)
     * places submitted attempts first and falls back to creation
     * timestamp for in-progress / abandoned ones. No eager fetch
     * because the analytics service only reads scalar fields.
     */
    List<QuizAttempt> findByQuizTeacherIdOrderByCompletedAtDescCreatedAtDesc(Long teacherId);

    /**
     * Bulk teacher-student overview read. Adds {@code student.currentLevel}
     * to the eager fetch so the rendered roster table can display the
     * level badge for each student without an N+1 lazy load.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user", "student.currentLevel" })
    List<QuizAttempt> findByQuizTeacherIdAndStudentIdInOrderByCompletedAtDescCreatedAtDesc(Long teacherId, List<Long> studentIds);

    /**
     * Submitted attempts on one quiz \u2014 used by the teacher quiz
     * detail panel. Service layer filters in JVM by
     * {@code hasCurrentAttemptAccess} and then paginates the result.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(Long quizId);

    /**
     * IDOR-safe single-row lookup for the student attempt-result page.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndStudentUserId(Long id, Long studentUserId);

    /**
     * IDOR-safe single-row lookup for the teacher attempt-review page.
     * Adds {@code quiz.teacher} + {@code quiz.teacher.user} to the
     * eager fetch so the teacher attribution is hydrated in one query.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);

    /**
     * Student-scoped attempts list used by analytics, weak-areas
     * extraction and the average-quiz-percent calculator.
     */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByStudentIdOrderByCompletedAtDescCreatedAtDesc(Long studentId);

    /**
     * Existence check for the teacher-feedback gating: returns
     * {@code true} only when {@code studentId} has any attempt on a
     * quiz authored by {@code teacherUserId}. Mirror of
     * {@code LessonProgressRepository.existsByStudentIdAndLessonTeacherUserId}.
     */
    boolean existsByStudentIdAndQuizTeacherUserId(Long studentId, Long teacherUserId);

    /**
     * Lesson-scoped variant of the feedback gating: returns {@code true}
     * only when {@code studentId} has an attempt on a quiz attached to
     * the specific {@code lessonId} <em>and</em> authored by
     * {@code teacherUserId}.
     */
    boolean existsByStudentIdAndQuizLessonIdAndQuizTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);

    /**
     * Teacher-side guard: returns {@code true} once any submitted
     * attempt exists for the quiz, blocking destructive question
     * edits that would invalidate already-graded student answers.
     */
    boolean existsByQuizIdAndSubmittedTrue(Long quizId);
}
