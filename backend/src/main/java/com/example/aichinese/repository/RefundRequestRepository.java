package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.enums.RefundStatus;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    long countByRefundStatus(RefundStatus refundStatus);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    List<RefundRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByOrderIdAndRefundStatusIn(Long orderId, List<RefundStatus> statuses);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Optional<RefundRequest> findByIdAndStudentUserId(Long id, Long userId);

    /**
     * Eager-fetch override so the admin refund-management view does not
     * trigger a lazy SELECT per row when the mapper accesses the student or
     * the originating order. Without this override, {@code findAll()}
     * silently regresses into an N+1 storm whenever the admin opens the page.
     */
    @Override
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @NonNull
    List<RefundRequest> findAll();

    /**
     * Sort-aware variant used by the admin refund overview so ordering is
     * delegated to the database.
     */
    @Override
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @NonNull
    List<RefundRequest> findAll(@NonNull Sort sort);
}
