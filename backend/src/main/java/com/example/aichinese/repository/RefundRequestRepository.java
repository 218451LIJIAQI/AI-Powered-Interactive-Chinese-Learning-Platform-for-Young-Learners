package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.enums.RefundStatus;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    long countByRefundStatus(RefundStatus refundStatus);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    List<RefundRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByOrderIdAndRefundStatusIn(Long orderId, List<RefundStatus> statuses);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Optional<RefundRequest> findByIdAndStudentUserId(Long id, Long userId);
}
