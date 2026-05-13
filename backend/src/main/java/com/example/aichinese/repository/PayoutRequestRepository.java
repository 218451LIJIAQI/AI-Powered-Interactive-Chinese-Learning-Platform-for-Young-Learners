package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.enums.PayoutStatus;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    long countByPayoutStatus(PayoutStatus payoutStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    List<PayoutRequest> findByTeacherUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Optional<PayoutRequest> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Eager-fetch override so the admin payout view never lazy-loads
     * teacher / teacher.user per-row when the response mapper runs. Mirrors
     * the same fix applied to RefundRequestRepository.
     */
    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @NonNull
    List<PayoutRequest> findAll();

    /**
     * Sort-aware variant for the admin payout-management screen so ordering is
     * resolved at the database layer instead of in JVM memory.
     */
    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @NonNull
    List<PayoutRequest> findAll(@NonNull Sort sort);

    @Query("select coalesce(sum(pr.amount), 0) from PayoutRequest pr where pr.teacher.id = :teacherId and pr.payoutStatus in :statuses")
    java.math.BigDecimal sumAmountByTeacherIdAndPayoutStatusIn(
            @Param("teacherId") Long teacherId,
            @Param("statuses") Set<PayoutStatus> statuses);
}
