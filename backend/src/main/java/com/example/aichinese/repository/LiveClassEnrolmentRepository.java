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

    @EntityGraph(attributePaths = {
            "liveClass",
            "liveClass.teacher",
            "liveClass.teacher.user",
            "liveClass.lesson",
            "liveClass.coursePackage",
            "student",
            "student.user"
    })
    List<LiveClassEnrolment> findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    @EntityGraph(attributePaths = {
            "liveClass",
            "liveClass.teacher",
            "liveClass.teacher.user",
            "student",
            "student.user",
            "student.currentLevel"
    })
    List<LiveClassEnrolment> findByLiveClassTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    long countByLiveClassIdAndEnrolmentStatus(Long liveClassId, LiveClassEnrolmentStatus enrolmentStatus);

    boolean existsByLiveClassIdAndStudentIdAndEnrolmentStatus(
            Long liveClassId,
            Long studentId,
            LiveClassEnrolmentStatus enrolmentStatus);

    boolean existsByStudentIdAndLiveClassTeacherUserId(Long studentId, Long teacherUserId);

    boolean existsByStudentIdAndLiveClassLessonIdAndLiveClassTeacherUserId(
            Long studentId,
            Long lessonId,
            Long teacherUserId);

    @EntityGraph(attributePaths = {
            "liveClass",
            "liveClass.teacher",
            "liveClass.teacher.user",
            "liveClass.lesson",
            "liveClass.coursePackage",
            "student",
            "student.user"
    })
    List<LiveClassEnrolment> findByLiveClassTeacherIdAndEnrolmentStatusNot(
            Long teacherId,
            LiveClassEnrolmentStatus excludedStatus);
}
