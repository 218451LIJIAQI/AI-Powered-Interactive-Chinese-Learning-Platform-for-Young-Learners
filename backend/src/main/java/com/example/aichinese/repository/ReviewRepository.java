package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.enums.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Override
    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    @NonNull
    List<Review> findAll();

    @Override
    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    @NonNull
    List<Review> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    List<Review> findByReviewerUserIdOrderByUpdatedAtDesc(Long reviewerUserId);

    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    Optional<Review> findByIdAndReviewerUserId(Long id, Long reviewerUserId);

    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    Optional<Review> findByReviewerIdAndTargetTypeAndTargetId(Long reviewerId, String targetType, Long targetId);

    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    List<Review> findByTargetTypeAndTargetIdAndReviewStatusOrderByCreatedAtDesc(
            String targetType,
            Long targetId,
            ReviewStatus reviewStatus);

    /**
     * Bulk-fetch reviews whose (targetType, targetId) tuple belongs to the
     * supplied collection. Used by the teacher-reviews view: instead of
     * loading every review row and resolving its target one-by-one (the
     * previous 2N+1 anti-pattern), the teacher service first collects the
     * lesson + package ids it owns, then issues a single bounded query per
     * target type. Empty collections are handled by callers (Hibernate
     * rejects empty `IN ()` clauses on some dialects).
     */
    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    List<Review> findByTargetTypeAndTargetIdInOrderByCreatedAtDesc(
            String targetType,
            Collection<Long> targetIds);

    /**
     * One row per distinct {@code reviewStatus} for the admin analytics
     * dashboard. Replaces the previous {@code findAll() + JVM group-by} which
     * materialised every review in memory just to count them by status.
     */
    @org.springframework.data.jpa.repository.Query(
            "select r.reviewStatus as status, count(r) as count from Review r group by r.reviewStatus")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByReviewStatus();
}
