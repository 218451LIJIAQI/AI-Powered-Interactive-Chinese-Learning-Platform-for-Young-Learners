package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherVerificationReview;

public interface TeacherVerificationReviewRepository extends JpaRepository<TeacherVerificationReview, Long> {

    /** Every decision on the application, oldest first (its history). */
    @EntityGraph(attributePaths = { "reviewerUser" })
    List<TeacherVerificationReview> findByTeacherIdOrderByReviewedAtAscIdAsc(Long teacherId);

    /** Decisions keep their record when the teacher deletes the account; only the link to the deleted file goes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update teacher_verification_reviews set document_id = null where teacher_id = :teacherId",
            nativeQuery = true)
    int detachDocuments(@Param("teacherId") Long teacherId);

    @EntityGraph(attributePaths = { "reviewerUser" })
    Optional<TeacherVerificationReview> findTopByTeacherIdOrderByReviewedAtDesc(Long teacherId);
}
