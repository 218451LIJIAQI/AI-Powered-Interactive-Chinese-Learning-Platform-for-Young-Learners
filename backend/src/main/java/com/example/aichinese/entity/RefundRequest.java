package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.RefundStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Student-initiated request to refund a previously settled {@link Order}.
 *
 * <p>The lifecycle follows
 * {@link com.example.aichinese.entity.enums.RefundStatus}:
 * {@code PENDING → APPROVED → REFUNDED}, or
 * {@code PENDING → REJECTED}. Approved-but-not-yet-settled requests
 * sit in {@code APPROVED} until the provider returns the funds, at
 * which point {@code RefundService} flips the row to {@code REFUNDED}
 * and cascades the change into:</p>
 * <ul>
 *   <li>{@link Order#paymentStatus} → {@code REFUNDED}</li>
 *   <li>Each affected {@link PaymentEntitlement} → {@code active = false}</li>
 *   <li>Each affected {@link TeacherEarning} → {@code REFUNDED}
 *       (clawback so the amount no longer counts toward payout)</li>
 * </ul>
 *
 * <p>{@link #amount} is captured at request time so the eventual
 * settlement amount is fixed even if the order total is later
 * partially refunded through other channels.</p>
 *
 * <p>{@link #version} guards against concurrent admin decisions on the
 * same request.</p>
 *
 * <h3>Field mutability contract</h3>
 * <ul>
 *   <li><b>Immutable</b>: {@link #student}, {@link #order},
 *       {@link #amount}, {@link #reason} — the request identity (who,
 *       what order, captured settlement amount, student rationale) is
 *       fixed at submission time. The captured {@link #amount} in
 *       particular must stay frozen so partial refunds through other
 *       channels never retroactively alter this row's settlement
 *       target.</li>
 *   <li><b>Mutable</b>: {@link #refundStatus} (state machine driven
 *       by {@code RefundService} approve/reject/applyRefund actions)
 *       and {@link #decisionReason} (filled by admin at the rejection
 *       or settlement step). Both are guarded by the
 *       {@link Version @Version} column above.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refund_requests", indexes = {
        @Index(name = "idx_refund_requests_student_id", columnList = "student_id"),
        @Index(name = "idx_refund_requests_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_requests_status", columnList = "refund_status"),
        @Index(name = "idx_refund_requests_student_created_at", columnList = "student_id,created_at")
})
public class RefundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private RefundStatus refundStatus = RefundStatus.PENDING;
}
