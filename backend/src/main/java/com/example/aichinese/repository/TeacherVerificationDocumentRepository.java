package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherVerificationDocument;

public interface TeacherVerificationDocumentRepository extends JpaRepository<TeacherVerificationDocument, Long> {

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Page<TeacherVerificationDocument> findByTeacherIdOrderBySubmittedAtDesc(Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    List<TeacherVerificationDocument> findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findById(Long id);

    long countByTeacherId(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findTopByTeacherIdOrderBySubmittedAtDesc(Long teacherId);

    default List<TeacherVerificationDocument> findLatestByTeacherIdIn(Collection<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return List.of();
        }
        return findLatestByTeacherIdInInternal(teacherIds);
    }

    @Query(value = """
            SELECT
                ranked.id,
                ranked.created_at,
                ranked.updated_at,
                ranked.teacher_id,
                ranked.uploaded_file_id,
                ranked.document_type,
                ranked.document_note,
                ranked.submitted_at,
                ranked.current_version,
                ranked.current_document_key
            FROM (
                SELECT d.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY d.teacher_id
                           ORDER BY d.submitted_at DESC, d.id DESC
                       ) AS rn
                FROM teacher_verification_documents d
                WHERE d.teacher_id IN (:teacherIds)
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<TeacherVerificationDocument> findLatestByTeacherIdInInternal(
            @Param("teacherIds") Collection<Long> teacherIds);

    default List<Object[]> countByTeacherIdIn(Collection<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return List.of();
        }
        return countByTeacherIdInInternal(teacherIds);
    }

    @Query("SELECT d.teacher.id, COUNT(d) FROM TeacherVerificationDocument d "
            + "WHERE d.teacher.id IN :teacherIds GROUP BY d.teacher.id")
    List<Object[]> countByTeacherIdInInternal(@Param("teacherIds") Collection<Long> teacherIds);
}
