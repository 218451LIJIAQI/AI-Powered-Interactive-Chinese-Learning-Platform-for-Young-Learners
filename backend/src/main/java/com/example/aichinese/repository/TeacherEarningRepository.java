package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.enums.TeacherEarningStatus;

/**
 * Persistence operations for {@link TeacherEarning} \u2014 the
 * commission ledger row attached to each {@code OrderItem} that
 * captures gross / net amounts and the payout-eligibility status
 * lifecycle ({@code PENDING \u2192 AVAILABLE \u2192 REFUNDED}).
 *
 * <p>Reads eager-fetch {@code teacher} + {@code teacher.user} +
 * {@code orderItem} + {@code orderItem.order} so the teacher
 * earnings dashboard renders the buyer / artefact context in a single
 * round trip. The sum-by-teacher aggregates are pushed entirely into
 * SQL with {@code coalesce(..., 0)} so callers receive a non-null
 * BigDecimal even when no earnings yet match.</p>
 */
public interface TeacherEarningRepository extends JpaRepository<TeacherEarning, Long> {

    /**
     * Unbounded teacher earnings list for the analytics service's
     * per-teacher snapshot. Backed by the {@code teacher.user_id}
     * foreign-key index.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "orderItem", "orderItem.order" })
    List<TeacherEarning> findByTeacherUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Paged earnings list for the teacher payout dashboard.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "orderItem", "orderItem.order" })
    Page<TeacherEarning> findByTeacherUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Idempotency probe used by
     * {@code TeacherEarningService.recordEarningsForOrder} to skip an
     * already-recorded order item, and by the refund flow to flip the
     * earning's status. Backed by the unique constraint on
     * {@code order_item_id} which guarantees at most one earning row
     * per order item.
     */
    Optional<TeacherEarning> findByOrderItemId(Long orderItemId);

    /**
     * Sum of every gross-amount row for a teacher \u2014 used by the
     * teacher dashboard's lifetime-revenue tile. Includes refunded
     * earnings; for a refund-aware sum use
     * {@link #sumGrossAmountByTeacherIdAndEarningStatusNot}.
     */
    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumGrossAmountByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * Sum of every net-amount row for a teacher \u2014 used by the
     * teacher dashboard's lifetime-net-revenue tile.
     */
    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumNetAmountByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * Refund-aware gross sum (typically called with
     * {@code earningStatus = REFUNDED}) so the payout-eligibility
     * calculation excludes refunded amounts.
     */
    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus <> :earningStatus")
    BigDecimal sumGrossAmountByTeacherIdAndEarningStatusNot(@Param("teacherId") Long teacherId, @Param("earningStatus") TeacherEarningStatus earningStatus);

    /**
     * Refund-aware net sum used by
     * {@code PayoutService.requireCommittedPayoutCovered} to ensure a
     * teacher cannot have more disbursement committed than their
     * non-refunded earnings cover.
     */
    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus <> :earningStatus")
    BigDecimal sumNetAmountByTeacherIdAndEarningStatusNot(@Param("teacherId") Long teacherId, @Param("earningStatus") TeacherEarningStatus earningStatus);

    /**
     * Sum of net-amount rows currently in {@code AVAILABLE} status \u2014
     * the immediate-payout-eligible balance shown to the teacher
     * before they file a payout request.
     */
    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus = com.example.aichinese.entity.enums.TeacherEarningStatus.AVAILABLE")
    BigDecimal sumAvailableNetAmountByTeacherId(@Param("teacherId") Long teacherId);
}
