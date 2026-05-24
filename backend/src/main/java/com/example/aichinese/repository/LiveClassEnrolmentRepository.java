package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;

/**
 * Persistence operations for {@link LiveClassEnrolment} \u2014 the
 * many-to-many join row between a {@code StudentProfile} and a
 * {@code LiveClass} carrying enrolment status (REGISTERED, CANCELLED,
 * COMPLETED) and the registration timestamp.
 *
 * <p>The eager-fetch graphs deliberately vary by access pattern: the
 * roster reads only need teacher/student metadata, the student "my
 * upcoming classes" list additionally needs the linked lesson /
 * coursePackage so the card can render the parent artefact, and the
 * teacher-student overview also pulls {@code student.currentLevel} to
 * render the level badge. Centralising on a single broadest graph
 * would force every read to materialise the worst-case join shape, so
 * the per-method graphs are kept as intentional optimisations.</p>
 *
 * <p>The {@code existsBy*} predicates that traverse
 * {@code liveClass.teacher.user} provide the cross-cutting feedback
 * gating mirror of {@code LessonProgressRepository.existsBy*} \u2014 a
 * teacher may feedback on a learner whose enrolment touches one of the
 * teacher's live classes, regardless of whether that learner has any
 * accompanying lesson progress.</p>
 */
public interface LiveClassEnrolmentRepository extends JpaRepository<LiveClassEnrolment, Long> {

    /**
     * Looks up a student's enrolment in a specific live class. Backed
     * by the unique constraint on {@code (live_class_id, student_id)}
     * \u2014 the cancellation flow uses this for status transitions.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    Optional<LiveClassEnrolment> findByLiveClassIdAndStudentId(Long liveClassId, Long studentId);

    /**
     * Roster of every learner registered to {@code liveClassId} ordered
     * by the registration timestamp. Used by the teacher's class detail
     * panel.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    List<LiveClassEnrolment> findByLiveClassIdOrderByEnrolledAtAsc(Long liveClassId);

    /**
     * Paged variant of {@link #findByLiveClassIdOrderByEnrolledAtAsc(Long)}
     * for endpoints that page through long rosters.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    Page<LiveClassEnrolment> findByLiveClassIdOrderByEnrolledAtAsc(Long liveClassId, Pageable pageable);

    /**
     * Student-facing "my live classes" list. Pulls in the parent
     * {@code lesson} and {@code coursePackage} so the rendered card
     * can show the wrapping artefact title; ordered by upcoming class
     * start time so soonest-first is the natural order.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "liveClass.lesson", "liveClass.coursePackage", "student", "student.user" })
    List<LiveClassEnrolment> findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    /**
     * Bulk teacher-student overview read. Adds {@code student.currentLevel}
     * to the eager fetch so the rendered roster table can display the
     * level badge for each enrolled learner without an N+1 lazy load.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user", "student.currentLevel" })
    List<LiveClassEnrolment> findByLiveClassTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    /**
     * Counts active enrolments per status on a single live class; backs
     * the capacity-check before allowing a new registration.
     */
    long countByLiveClassIdAndEnrolmentStatus(Long liveClassId, LiveClassEnrolmentStatus enrolmentStatus);

    /**
     * Existence guard for the registration flow: prevents a learner
     * from re-enrolling in a class they are already actively registered
     * for. Pairs with the unique constraint as the in-flight check; the
     * constraint remains the race-safe enforcement boundary.
     */
    boolean existsByLiveClassIdAndStudentIdAndEnrolmentStatus(
            Long liveClassId,
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    /**
     * Existence check for the teacher-feedback gating: returns
     * {@code true} only when {@code studentId} is enrolled in
     * <em>any</em> live class taught by {@code teacherUserId}. Mirror
     * of {@code LessonProgressRepository.existsByStudentIdAndLessonTeacherUserId}.
     */
    boolean existsByStudentIdAndLiveClassTeacherUserId(Long studentId, Long teacherUserId);

    /**
     * Lesson-scoped variant of the feedback gating: returns
     * {@code true} only when {@code studentId} is enrolled in a live
     * class for the specific {@code lessonId} <em>and</em> taught by
     * {@code teacherUserId}.
     */
    boolean existsByStudentIdAndLiveClassLessonIdAndLiveClassTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);

    /**
     * Returns every enrolment for one teacher's classes that has not
     * been cancelled (or whatever {@code excludedStatus} is passed) \u2014
     * used by the teacher dashboard and reporting flow. The eager fetch
     * mirrors the student-facing list so the response mapper can
     * resolve the parent lesson / coursePackage in the same round trip.
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "liveClass.lesson", "liveClass.coursePackage", "student", "student.user" })
    List<LiveClassEnrolment> findByLiveClassTeacherIdAndEnrolmentStatusNot(
            Long teacherId,
            LiveClassEnrolmentStatus excludedStatus);
}
