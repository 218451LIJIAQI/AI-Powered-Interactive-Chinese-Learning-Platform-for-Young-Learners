package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface ClassEnrolmentRepository extends JpaRepository<ClassEnrolment, Long> {

    boolean existsByStudentIdAndCoursePackageId(Long studentId, Long coursePackageId);

    @EntityGraph(attributePaths = {
            "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user",
            "coursePackage.level", "coursePackage.category" })
    List<ClassEnrolment> findByStudentIdOrderByJoinedAtDesc(Long studentId);

    @Query("select e.coursePackage.id from ClassEnrolment e where e.student.id = :studentId")
    List<Long> findClassIdsByStudentId(@Param("studentId") Long studentId);

    /** Enrolments in a teacher's classes, with the student and the class, newest first. */
    @EntityGraph(attributePaths = { "student", "student.user", "coursePackage" })
    List<ClassEnrolment> findByCoursePackageTeacherIdOrderByJoinedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "student", "student.user", "coursePackage" })
    List<ClassEnrolment> findByCoursePackageTeacherIdAndStudentId(Long teacherId, Long studentId);

    long countByCoursePackageId(Long coursePackageId);

    @Query("select e.coursePackage.id as id, count(e) as total from ClassEnrolment e "
            + "where e.coursePackage.id in :classIds group by e.coursePackage.id")
    List<IdCountProjection> countByClassIdIn(@Param("classIds") Collection<Long> classIds);

}
