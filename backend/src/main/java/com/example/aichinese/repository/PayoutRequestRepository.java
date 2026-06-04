package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.enums.PayoutStatus;

import jakarta.persistence.LockModeType;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    long countByPayoutStatus(PayoutStatus payoutStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Page<PayoutRequest> findByTeacherUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Optional<PayoutRequest> findByIdAndTeacherUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @Query("select pr from PayoutRequest pr where pr.id = :id")
    Optional<PayoutRequest> findLockedById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @NonNull
    Page<PayoutRequest> findAll(@NonNull Pageable pageable);

    default BigDecimal sumAmountByTeacherIdAndPayoutStatusIn(
            Long teacherId,
            @Nullable Set<PayoutStatus> statuses) {
        if (teacherId == null || statuses == null || statuses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sumAmountByTeacherIdAndPayoutStatusInInternal(teacherId, statuses);
    }

    @Query("select coalesce(sum(pr.amount), 0) from PayoutRequest pr where pr.teacher.id = :teacherId and pr.payoutStatus in :statuses")
    BigDecimal sumAmountByTeacherIdAndPayoutStatusInInternal(
            @Param("teacherId") Long teacherId,
            @Param("statuses") Set<PayoutStatus> statuses);
}
