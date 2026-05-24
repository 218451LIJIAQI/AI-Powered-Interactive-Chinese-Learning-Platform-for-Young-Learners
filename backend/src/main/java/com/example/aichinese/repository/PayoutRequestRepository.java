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

import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.enums.PayoutStatus;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link PayoutRequest} — the teacher's
 * request to disburse their accumulated earnings, progressing through
 * {@code PENDING → APPROVED → PAID} (or {@code REJECTED}).
 *
 * <p>Every read eager-fetches {@code teacher} + {@code teacher.user}
 * so the admin payout-management screen and the teacher "my payouts"
 * page can render owner metadata in a single round trip. The
 * write-path {@link #findLockedById(Long)} variant pairs with the
 * {@code requireCommittedPayoutCovered} guard in
 * {@code PayoutService} to prevent a teacher from being paid out
 * twice for the same earnings under concurrent admin actions.</p>
 */
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    /**
     * Backs the admin dashboard's "pending payouts" counter tile.
     * Backed by the implicit index on the payout-status enum column.
     */
    long countByPayoutStatus(PayoutStatus payoutStatus);

    /**
     * Paged teacher "my payouts" history.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Page<PayoutRequest> findByTeacherUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup so a teacher cannot surface another
     * teacher's payout request by guessing its id.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Optional<PayoutRequest> findByIdAndTeacherUserId(Long id, Long userId);

    /**
     * Pessimistic-write variant for the admin approve / reject /
     * mark-paid flows so two concurrent admin actions cannot both
     * transition the same payout request and double-disburse the
     * teacher.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @Query("select pr from PayoutRequest pr where pr.id = :id")
    Optional<PayoutRequest> findLockedById(@Param("id") Long id);

    /**
     * Admin payouts page. The named eager-fetch keeps the response
     * mapper from triggering one lazy SELECT per row.
     */
    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    @NonNull
    Page<PayoutRequest> findAll(@NonNull Pageable pageable);

    /**
     * Sums payout amounts for a single teacher across the supplied
     * statuses; the {@code coalesce(..., 0)} wrapper avoids null when
     * no payouts yet match. Used by
     * {@code PayoutService.requireCommittedPayoutCovered} to ensure a
     * teacher cannot have more disbursement committed than their
     * earned balance covers.
     */
    @Query("select coalesce(sum(pr.amount), 0) from PayoutRequest pr where pr.teacher.id = :teacherId and pr.payoutStatus in :statuses")
    BigDecimal sumAmountByTeacherIdAndPayoutStatusIn(
            @Param("teacherId") Long teacherId,
            @Param("statuses") Set<PayoutStatus> statuses);
}
