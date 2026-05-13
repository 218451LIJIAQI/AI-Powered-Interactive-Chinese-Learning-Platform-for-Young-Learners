package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

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
    @NonNull
    List<Report> findAll();

    /**
     * Sort-aware overload so the admin report listing pushes ordering down to
     * the database rather than materialising the full result set first and
     * sorting in JVM memory. EntityGraph remains identical so the response
     * mapper does not trigger lazy SELECTs on reporter/teacher relationships.
     */
    @Override
    @EntityGraph(attributePaths = { "reporter", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    List<Report> findAll(@NonNull Sort sort);

    /**
     * Returns one {@link com.example.aichinese.repository.projection.StatusCountProjection}
     * row per distinct {@code reportStatus}, so the admin analytics dashboard
     * can build the status breakdown with a single {@code GROUP BY} query
     * instead of loading every report into JVM memory and counting there.
     */
    @org.springframework.data.jpa.repository.Query(
            "select r.reportStatus as status, count(r) as count from Report r group by r.reportStatus")
    java.util.List<com.example.aichinese.repository.projection.StatusCountProjection> groupByReportStatus();

    /**
     * Bulk count for the admin analytics overview — replaces the previous
     * {@code findAll().stream().filter(s == PENDING).count()} which paid a
     * full table load for a single number.
     */
    long countByReportStatus(com.example.aichinese.entity.enums.ReportStatus status);
}
