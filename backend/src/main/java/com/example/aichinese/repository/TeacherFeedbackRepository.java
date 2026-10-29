package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.TeacherFeedback;

public interface TeacherFeedbackRepository
        extends JpaRepository<TeacherFeedback, Long>, JpaSpecificationExecutor<TeacherFeedback> {

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Page<TeacherFeedback> findAll(Specification<TeacherFeedback> specification, Pageable pageable);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Optional<TeacherFeedback> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByStudentIdAndCoursePackageIdOrderByCreatedAtDescIdDesc(Long studentId, Long coursePackageId);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByTeacherIdAndStudentIdOrderByCreatedAtDescIdDesc(Long teacherId, Long studentId);

    long countByStudentIdAndCoursePackageId(Long studentId, Long coursePackageId);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByTeacherIdOrderByCreatedAtDescIdDesc(Long teacherId);
}
