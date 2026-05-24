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

import com.example.aichinese.entity.TeacherVerificationReview;

/**
 * Persistence operations for {@link TeacherVerificationReview} \u2014
 * the audit record of an admin's APPROVE or REJECT decision against a
 * teacher's verification submission. Each read eager-fetches the
 * {@code reviewerUser}, the {@code document}, and that document's
 * {@code uploadedFile} so the admin / teacher detail panels render
 * the full review row in one round trip.
 */
public interface TeacherVerificationReviewRepository extends JpaRepository<TeacherVerificationReview, Long> {

    /**
     * Paged review history for one teacher \u2014 used by the admin
     * verification detail panel.
     */
    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Page<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtDesc(Long teacherId, Pageable pageable);

    /**
     * Returns the latest review for a teacher; used by
     * {@code TeacherManagementService} to surface the most-recent admin
     * decision on the teacher's profile dashboard.
     */
    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Optional<TeacherVerificationReview> findTopByTeacherIdOrderByReviewedAtDesc(Long teacherId);

    /**
     * Returns the most-recently-reviewed review for every teacher id in
     * {@code teacherIds}. Batch-loaded once per admin teacher-list page
     * to avoid the N-query anti-pattern. Short-circuits the empty-input
     * case so the underlying native SQL never emits {@code IN ()}
     * (which MySQL rejects with a syntax error \u2014 Hibernate only
     * rewrites empty-{@code IN} for JPQL, not native queries). The
     * window function uses {@code id} as a deterministic tie-breaker
     * when reviews share the same timestamp.
     */
    default List<TeacherVerificationReview> findLatestByTeacherIdIn(Collection<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return List.of();
        }
        return findLatestByTeacherIdInInternal(teacherIds);
    }

    /**
     * Native-SQL backing query for
     * {@link #findLatestByTeacherIdIn(Collection)}. Not intended to be
     * invoked directly \u2014 the public default-method wrapper
     * provides the empty-collection guard.
     */
    @Query(value = """
            SELECT
                ranked.id,
                ranked.created_at,
                ranked.updated_at,
                ranked.teacher_id,
                ranked.document_id,
                ranked.reviewer_user_id,
                ranked.decision_status,
                ranked.review_note,
                ranked.reviewed_at
            FROM (
                SELECT r.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.teacher_id
                           ORDER BY r.reviewed_at DESC, r.id DESC
                       ) AS rn
                FROM teacher_verification_reviews r
                WHERE r.teacher_id IN (:teacherIds)
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<TeacherVerificationReview> findLatestByTeacherIdInInternal(
            @Param("teacherIds") Collection<Long> teacherIds);
}
