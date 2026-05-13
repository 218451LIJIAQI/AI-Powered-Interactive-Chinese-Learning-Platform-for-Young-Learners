package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;

public interface LiveClassEnrolmentRepository extends JpaRepository<LiveClassEnrolment, Long> {

    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    Optional<LiveClassEnrolment> findByLiveClassIdAndStudentId(Long liveClassId, Long studentId);

    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "student", "student.user" })
    List<LiveClassEnrolment> findByLiveClassIdOrderByEnrolledAtAsc(Long liveClassId);

    @EntityGraph(attributePaths = { "liveClass", "liveClass.teacher", "liveClass.teacher.user", "liveClass.lesson", "liveClass.coursePackage", "student", "student.user" })
    List<LiveClassEnrolment> findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    long countByLiveClassIdAndEnrolmentStatus(Long liveClassId, LiveClassEnrolmentStatus enrolmentStatus);

    boolean existsByLiveClassIdAndStudentIdAndEnrolmentStatus(
            Long liveClassId,
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    boolean existsByStudentIdAndLiveClassTeacherUserId(Long studentId, Long teacherUserId);

    /**
     * Counts active enrolments (i.e. anything other than {@code CANCELLED})
     * across every live class owned by the given teacher. Used by the
     * teacher analytics overview so it no longer has to load every enrolment
     * row in the database and filter in JVM memory.
     */
    long countByLiveClassTeacherIdAndEnrolmentStatusNot(
            Long teacherId,
            LiveClassEnrolmentStatus excludedStatus);
}
