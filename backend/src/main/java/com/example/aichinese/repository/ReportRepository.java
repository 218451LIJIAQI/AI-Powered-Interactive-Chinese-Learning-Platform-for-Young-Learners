package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    List<Report> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    List<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    Optional<Report> findByIdAndReporterId(Long id, Long reporterId);

    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    List<Report> findByRelatedTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId);

    @Override
    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    List<Report> findAll();
}
