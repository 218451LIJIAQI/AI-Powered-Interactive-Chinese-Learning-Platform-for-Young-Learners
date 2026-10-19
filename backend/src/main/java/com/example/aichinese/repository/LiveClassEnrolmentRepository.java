package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;

public interface LiveClassEnrolmentRepository extends JpaRepository<LiveClassEnrolment, Long> {

    /**
     * The current (ENROLLED or ATTENDED) enrolment of a student in a class. Cancelled rows are kept as
     * history, so a student may own several rows per class but at most one active one
     * (uk_live_class_enrolments_active_student).
     */
    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    Optional<LiveClassEnrolment> findFirstByLiveClassIdAndStudentIdAndEnrolmentStatusInOrderByIdDesc(
            Long liveClassId,
            Long studentId,
            Collection<LiveClassEnrolmentStatus> statuses);

    boolean existsByStudentIdAndLiveClassTeacherUserIdAndEnrolmentStatusNot(
            Long studentId,
            Long teacherUserId,
            LiveClassEnrolmentStatus excludedStatus);

    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    List<LiveClassEnrolment> findByLiveClassIdOrderByEnrolledAtAsc(Long liveClassId);

    @EntityGraph(attributePaths = { "liveClass", "liveClass.lesson", "liveClass.coursePackage", "student" })
    @Query("""
            select enrolment from LiveClassEnrolment enrolment
            join enrolment.liveClass liveClass
            where enrolment.student.id = :studentId
              and enrolment.enrolmentStatus = :status
              and liveClass.scheduledStartAt > :now
              and liveClass.liveClassStatus not in :closedStatuses
            """)
    List<LiveClassEnrolment> findUpcomingForStudent(
            @Param("studentId") Long studentId,
            @Param("status") LiveClassEnrolmentStatus status,
            @Param("now") LocalDateTime now,
            @Param("closedStatuses") Set<LiveClassStatus> closedStatuses);

    @Query("""
            select count(enrolment) from LiveClassEnrolment enrolment
            join enrolment.liveClass liveClass
            where liveClass.teacher.id = :teacherId
              and enrolment.enrolmentStatus <> :cancelledStatus
            """)
    long countActiveByTeacherId(
            @Param("teacherId") Long teacherId,
            @Param("cancelledStatus") LiveClassEnrolmentStatus cancelledStatus);

    @EntityGraph(attributePaths = {
            "liveClass",
            "liveClass.teacher",
            "liveClass.teacher.user",
            "student",
            "student.user",
            "student.currentLevel"
    })
    List<LiveClassEnrolment> findByLiveClassTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    long countByLiveClassIdAndEnrolmentStatusIn(Long liveClassId, Collection<LiveClassEnrolmentStatus> statuses);

    boolean existsByLiveClassIdAndStudentIdAndEnrolmentStatusIn(
            Long liveClassId,
            Long studentId,
            Collection<LiveClassEnrolmentStatus> statuses);

    boolean existsByStudentIdAndLiveClassLessonIdAndLiveClassTeacherUserIdAndEnrolmentStatusNot(
            Long studentId,
            Long lessonId,
            Long teacherUserId,
            LiveClassEnrolmentStatus enrolmentStatus);
}
