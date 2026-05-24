package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherFeedback;

/**
 * Persistence operations for {@link TeacherFeedback} — free-text
 * comments a teacher leaves for a learner, optionally scoped to a
 * specific lesson or quiz attempt.
 *
 * <p>Every read references the {@link TeacherFeedback#GRAPH_DETAIL
 * named entity graph} so the teacher dashboard, student feedback
 * list and notification access checks all hydrate the standard
 * teacher / student / lesson / quizAttempt+quiz+quiz.lesson chain in
 * a single SQL round trip. Centralising the graph on the entity
 * eliminated the previous eight-fold duplication of an identical
 * attribute-path array.</p>
 */
public interface TeacherFeedbackRepository extends JpaRepository<TeacherFeedback, Long> {

    /**
     * Paged teacher feedback list backing the teacher dashboard's
     * "my feedback" page.
     */
    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Page<TeacherFeedback> findByTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId, Pageable pageable);

    /**
     * Bulk teacher-student overview read used by
     * {@code TeacherStudentService} to batch-load every feedback row
     * across the teacher's roster in one query.
     */
    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByTeacherIdAndStudentIdInOrderByCreatedAtDesc(Long teacherId, List<Long> studentIds);

    /**
     * Returns every feedback row addressed to a learner, used by the
     * student feedback page. Service-side filtering removes feedback
     * pointing at lessons the learner has lost access to (e.g. after
     * a refund or content archive).
     */
    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code TeacherFeedback → TeacherProfile → User} so a teacher
     * cannot mutate another teacher's feedback by guessing its id.
     */
    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Optional<TeacherFeedback> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    /**
     * IDOR-safe single-row lookup for the student-side read flow
     * (notification deep-link, feedback-detail page); pairs with the
     * service-side accessibility check on the linked lesson.
     */
    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Optional<TeacherFeedback> findByIdAndStudentUserId(Long id, Long studentUserId);
}
