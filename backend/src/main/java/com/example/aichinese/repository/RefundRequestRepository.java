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

/**
 * Persistence operations for {@link RefundRequest} \u2014 the learner's
 * request to refund an {@code Order}, progressing through
 * {@code PENDING \u2192 APPROVED \u2192 REFUNDED} (or {@code REJECTED}).
 *
 * <p>Every read eager-fetches {@code student} + {@code student.user}
 * + {@code order} so the admin refund-management view and the student
 * "my refund requests" page can render the buyer + order context in a
 * single round trip. The write-path
 * {@link #findLockedById(Long)} variant pairs with the
 * {@code requireTransition} state-machine guard in
 * {@code RefundService} to prevent two admins from racing the same
 * approval / rejection / mark-as-refunded transition.</p>
 */
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    /**
     * Backs the admin dashboard's "pending refunds" counter tile. Backed
     * by the implicit index on the refund-status enum column.
     */
    long countByRefundStatus(RefundStatus refundStatus);

    /**
     * Paged student "my refund requests" history.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Page<RefundRequest> findByStudentUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Guard against creating a duplicate active refund request on the
     * same order \u2014 used by the create-refund flow to surface a 400
     * before the database catches it.
     */
    boolean existsByOrderIdAndRefundStatusIn(Long orderId, Collection<RefundStatus> statuses);

    /**
     * IDOR-safe single-row lookup so a learner cannot surface another
     * learner's refund request by guessing its id.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    Optional<RefundRequest> findByIdAndStudentUserId(Long id, Long userId);

    /**
     * Pessimistic-write variant for the admin approve / reject /
     * mark-refunded flow so two concurrent admin actions cannot both
     * transition the same refund request and double-emit the same
     * notification / audit-log row. The lock pairs with the
     * {@code requireTransition} state-machine check in
     * {@code RefundService}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @Query("select rr from RefundRequest rr where rr.id = :id")
    Optional<RefundRequest> findLockedById(@Param("id") Long id);

    /**
     * Admin refund-management page. The eager fetch keeps the response
     * mapper from triggering one lazy SELECT per row.
     */
    @Override
    @EntityGraph(attributePaths = { "student", "student.user", "order" })
    @NonNull
    Page<RefundRequest> findAll(@NonNull Pageable pageable);
}
