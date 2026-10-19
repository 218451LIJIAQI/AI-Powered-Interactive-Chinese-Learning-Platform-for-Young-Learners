package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherFeedback;

public interface TeacherFeedbackRepository extends JpaRepository<TeacherFeedback, Long> {

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Page<TeacherFeedback> findByTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId, Pageable pageable);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByTeacherIdAndStudentIdInOrderByCreatedAtDesc(Long teacherId, List<Long> studentIds);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    List<TeacherFeedback> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Optional<TeacherFeedback> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(value = TeacherFeedback.GRAPH_DETAIL)
    Optional<TeacherFeedback> findByIdAndStudentUserId(Long id, Long studentUserId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "practiceSubmission" })
    List<TeacherFeedback> findByPracticeSubmissionIdInOrderByCreatedAtAsc(Collection<Long> practiceSubmissionIds);
}
