package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.enums.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Override
    @EntityGraph(attributePaths = { "reviewer", "reviewer.user" })
    List<Review> findAll();

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
}
