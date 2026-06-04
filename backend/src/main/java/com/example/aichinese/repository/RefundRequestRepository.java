package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.enums.RefundStatus;

import jakarta.persistence.LockModeType;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    long countByRefundStatus(RefundStatus refundStatus);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Page<RefundRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderIdAndRefundStatusIn(Long orderId, Collection<RefundStatus> statuses);

    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Optional<RefundRequest> findByIdAndStudentUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @Query("select rr from RefundRequest rr where rr.id = :id")
    Optional<RefundRequest> findLockedById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @NonNull
    Page<RefundRequest> findAll(@NonNull Pageable pageable);
}
