package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.TeacherEarningStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Commission ledger row attributing a single {@link OrderItem} to a
 * {@link TeacherProfile}.
 *
 * <p>Created by {@code TeacherEarningService} when an order moves to
 * {@link com.example.aichinese.entity.enums.PaymentStatus#PAID}.
 * Exactly one earning row exists per order item (enforced by the
 * unique constraint on {@code order_item_id}) so a refund can
 * idempotently flip {@link #earningStatus} from {@code AVAILABLE} to
 * {@code REFUNDED} without race conditions.</p>
 *
 * <p>The three monetary fields capture the full commission split at
 * settlement time:</p>
 * <ul>
 *   <li>{@link #grossAmount} — what the student paid for this line.</li>
 *   <li>{@link #commissionAmount} — the platform's cut (configured per
 *       teacher tier; defaults from {@code SystemSetting}).</li>
 *   <li>{@link #netAmount} — what the teacher is entitled to:
 *       {@code grossAmount - commissionAmount}.</li>
 * </ul>
 *
 * <p>All three are stored explicitly (rather than computed from a
 * commission rate) so that retroactive rate changes do not corrupt
 * historical earnings.</p>
 *
 * <p>{@link #version} guards against the rare race between a refund
 * and a payout calculation reading the same row.</p>
 *
 * <h3>Field mutability contract</h3>
 *
 * <p>The three monetary fields plus the identity FKs are mapped
 * {@code updatable = false}, making the Javadoc claim above
 * ("retroactive rate changes do not corrupt historical earnings")
 * a technical guarantee rather than a documentation promise.</p>
 *
 * <ul>
 *   <li><b>Immutable</b>: {@link #teacher}, {@link #orderItem},
 *       {@link #grossAmount}, {@link #commissionAmount},
 *       {@link #netAmount}. The single legitimate write path is
 *       {@code TeacherEarningService#createEarning}.</li>
 *   <li><b>Mutable</b>: {@link #earningStatus} only, flipped by
 *       {@code RefundService} from {@code AVAILABLE} to
 *       {@code REFUNDED} during clawback. Guarded by
 *       {@link Version @Version} above.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_earnings", indexes = {
        @Index(name = "idx_teacher_earnings_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_earnings_order_item_id", columnList = "order_item_id"),
        @Index(name = "idx_teacher_earnings_teacher_status_created", columnList = "teacher_id,earning_status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_earnings_order_item", columnNames = { "order_item_id" })
})
public class TeacherEarning extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_status", nullable = false, length = 30)
    private TeacherEarningStatus earningStatus = TeacherEarningStatus.AVAILABLE;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
