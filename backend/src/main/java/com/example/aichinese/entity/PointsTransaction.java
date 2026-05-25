package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only XP ledger entry recording a credit or debit on a
 * student's points balance.
 *
 * <p>The platform uses a strict event-sourced model for points: the
 * {@link StudentProfile#getTotalPoints()} field is a denormalized
 * cache of {@code SUM(pointsDelta)} grouped by {@code student_id}, and
 * every change must go through this ledger to remain consistent.</p>
 *
 * <p>The unique constraint on {@code (student_id, source_key)} provides
 * <strong>idempotency at the database level</strong>: when a quiz
 * completion fires twice (network retry, double-tap, webhook
 * redelivery), only the first row succeeds and the duplicate is silently
 * absorbed by the {@code DuplicateKeyException} handler in
 * {@code PointsService}. {@link #sourceKey} is a generated column
 * derived from {@link #sourceType} and {@link #sourceId} on the
 * database side, so application code only sets the latter two.</p>
 *
 * <p>{@link #pointsDelta} may be negative (e.g. badge revocation,
 * compensating entry for a refunded purchase) — clamping is performed
 * at the service layer rather than the column.</p>
 *
 * <p>The append-only contract is enforced at the ORM layer: every
 * domain field is mapped {@code updatable = false}, so any subsequent
 * {@code save()} on a managed instance silently drops attempted
 * mutations at flush time. This keeps the
 * {@code SUM(points_delta) GROUP BY student_id} aggregate that backs
 * {@link StudentProfile#getTotalPoints()} trustworthy even if a service
 * accidentally re-saves a ledger row. Reversals or corrections MUST
 * be expressed as a new ledger entry with an opposite-signed
 * {@link #pointsDelta}, never an in-place rewrite.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "points_transactions", indexes = {
        @Index(name = "idx_points_transactions_student_id", columnList = "student_id"),
        @Index(name = "idx_points_transactions_created_at", columnList = "created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_points_transactions_student_source", columnNames = { "student_id", "source_key" })
})
public class PointsTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "points_delta", nullable = false, updatable = false)
    private Integer pointsDelta;

    @Column(name = "reason", nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "source_type", updatable = false, length = 100)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "source_key", insertable = false, updatable = false, length = 220)
    private String sourceKey;
}
