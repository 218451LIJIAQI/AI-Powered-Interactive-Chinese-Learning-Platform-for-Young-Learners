package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherVerificationReview;

public interface TeacherVerificationReviewRepository extends JpaRepository<TeacherVerificationReview, Long> {

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    List<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Page<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtDesc(Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = { "reviewerUser", "document", "document.uploadedFile" })
    Optional<TeacherVerificationReview> findTopByTeacherIdOrderByReviewedAtDesc(Long teacherId);
}
