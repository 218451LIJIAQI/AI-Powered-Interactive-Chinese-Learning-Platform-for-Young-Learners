package com.example.aichinese.repository;

import java.time.LocalDateTime;
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
import com.example.aichinese.entity.enums.AccessType;

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
            left join liveClass.lesson lesson
            left join lesson.module module
            left join liveClass.coursePackage coursePackage
            where liveClass.teacher.id = :teacherId
              and enrolment.enrolmentStatus <> :cancelledStatus
              and (
                  lesson is null
                  or lesson.accessType <> :paidAccessType
                  or exists (
                      select lessonEntitlement.id from PaymentEntitlement lessonEntitlement
                      where lessonEntitlement.student.id = enrolment.student.id
                        and lessonEntitlement.active = true
                        and lessonEntitlement.itemType = 'LESSON'
                        and lessonEntitlement.itemId = lesson.id
                  )
                  or exists (
                      select moduleEntitlement.id from PaymentEntitlement moduleEntitlement
                      where moduleEntitlement.student.id = enrolment.student.id
                        and moduleEntitlement.active = true
                        and moduleEntitlement.itemType = 'MODULE'
                        and moduleEntitlement.itemId = module.id
                  )
              )
              and (
                  coursePackage is null
                  or coursePackage.accessType <> :paidAccessType
                  or exists (
                      select packageEntitlement.id from PaymentEntitlement packageEntitlement
                      where packageEntitlement.student.id = enrolment.student.id
                        and packageEntitlement.active = true
                        and packageEntitlement.itemType = 'PACKAGE'
                        and packageEntitlement.itemId = coursePackage.id
                  )
              )
            """)
    long countAccessibleByTeacherId(
            @Param("teacherId") Long teacherId,
            @Param("cancelledStatus") LiveClassEnrolmentStatus cancelledStatus,
            @Param("paidAccessType") AccessType paidAccessType);

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
