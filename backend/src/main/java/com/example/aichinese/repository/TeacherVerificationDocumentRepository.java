package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface TeacherVerificationDocumentRepository extends JpaRepository<TeacherVerificationDocument, Long> {

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    List<TeacherVerificationDocument> findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(Long teacherId);

    /** Every version the teacher ever uploaded (deleting an account removes them all). */
    @EntityGraph(attributePaths = { "uploadedFile" })
    List<TeacherVerificationDocument> findByTeacherId(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findById(Long id);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findTopByTeacherIdOrderBySubmittedAtDesc(Long teacherId);

    /** Current documents per teacher (one per type), for the application lists. */
    @Query("select d.teacher.id as id, count(d) as total from TeacherVerificationDocument d "
            + "where d.teacher.id in :teacherIds and d.currentVersion = true group by d.teacher.id")
    List<IdCountProjection> countCurrentByTeacherIdIn(@Param("teacherIds") Collection<Long> teacherIds);
}
