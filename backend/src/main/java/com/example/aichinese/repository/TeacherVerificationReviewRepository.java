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

public interface TeacherVerificationReviewRepository extends JpaRepository<TeacherVerificationReview, Long> {

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    List<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Page<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtDesc(Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Optional<TeacherVerificationReview> findTopByTeacherIdOrderByReviewedAtDesc(Long teacherId);

    /**
     * Returns the most-recently-reviewed review for every teacher id in
     * {@code teacherIds}. Batch-loaded once per admin teacher-list page to
     * avoid the N-query anti-pattern. Uses {@code id} as a deterministic
     * tie-breaker when reviews share the same timestamp.
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
    List<TeacherVerificationReview> findLatestByTeacherIdIn(
            @Param("teacherIds") Collection<Long> teacherIds);
}
