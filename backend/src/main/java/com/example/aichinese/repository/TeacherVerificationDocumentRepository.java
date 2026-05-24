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

/**
 * Persistence operations for {@link TeacherVerificationDocument} \u2014
 * the documents (ID, qualifications, etc.) a teacher uploads to support
 * their verification request. Each read eager-fetches
 * {@code uploadedFile}, {@code teacher} and {@code teacher.user} so the
 * admin verification queue and the teacher-side history page render in
 * one round trip.
 *
 * <p>Multiple versions of the same document type may coexist: the
 * latest upload is marked {@code currentVersion = true}; previous
 * uploads remain for audit. The current-only filter on
 * {@link #findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(Long)}
 * is used by the resubmission flow to flip the previous current
 * version to {@code false} before inserting the new row.</p>
 */
public interface TeacherVerificationDocumentRepository extends JpaRepository<TeacherVerificationDocument, Long> {

    /**
     * Paged document history for one teacher \u2014 used by the admin
     * verification detail panel.
     */
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Page<TeacherVerificationDocument> findByTeacherIdOrderBySubmittedAtDesc(Long teacherId, Pageable pageable);

    /**
     * Returns the current-version documents for a teacher, used by the
     * resubmission flow to flip-to-superseded before inserting the new
     * upload.
     */
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    List<TeacherVerificationDocument> findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(Long teacherId);

    /**
     * IDOR-safe single-row lookup for the teacher self-service download
     * flow: returns the document only when the teacher owns it.
     */
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Eager-fetch override of {@link JpaRepository#findById(Object)}
     * \u2014 used by admin moderation routes that have already confirmed
     * the caller's permissions through a higher-level check.
     */
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findById(Long id);

    /**
     * Counts how many verification documents a teacher has uploaded,
     * regardless of current-version flag. Used by the resubmission
     * gate ("at least one document required") and the admin approval
     * gate.
     */
    long countByTeacherId(Long teacherId);

    /**
     * Returns the single most-recent submission across every version
     * for one teacher. Used by the admin approve / reject flow when
     * recording the {@code TeacherVerificationReview} so the review
     * row links back to a representative document.
     */
    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findTopByTeacherIdOrderBySubmittedAtDesc(Long teacherId);

    /**
     * Returns the most-recently-submitted document for every teacher id
     * in {@code teacherIds}. Used by the admin teacher-list endpoint
     * to eliminate the N-query pattern: one call for the page, one
     * call here, one for reviews.
     *
     * <p>Short-circuits the empty-input case so the underlying native
     * SQL never emits {@code IN ()} (which MySQL rejects with a syntax
     * error \u2014 Hibernate only rewrites empty-{@code IN} for JPQL,
     * not native queries). The window function uses {@code id} as a
     * deterministic tie-breaker when multiple documents share the same
     * submission timestamp.</p>
     */
    default List<TeacherVerificationDocument> findLatestByTeacherIdIn(Collection<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return List.of();
        }
        return findLatestByTeacherIdInInternal(teacherIds);
    }

    /**
     * Native-SQL backing query for
     * {@link #findLatestByTeacherIdIn(Collection)}. Not intended to be
     * invoked directly \u2014 the public default-method wrapper provides
     * the empty-collection guard.
     */
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

    /**
     * Returns the document count per teacher for a batch of teacher
     * ids. Result rows are Object arrays: {@code [teacherId (Long),
     * count (Long)]}. Hibernate's JPQL empty-{@code IN} rewrite handles
     * the empty input case automatically here, so no wrapper guard is
     * required.
     */
    @Query("SELECT d.teacher.id, COUNT(d) FROM TeacherVerificationDocument d "
            + "WHERE d.teacher.id IN :teacherIds GROUP BY d.teacher.id")
    List<Object[]> countByTeacherIdIn(@Param("teacherIds") Collection<Long> teacherIds);
}
