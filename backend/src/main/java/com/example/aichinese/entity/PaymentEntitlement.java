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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Granted right of a {@link StudentProfile} to access a paid artefact.
 *
 * <p>This is the single source of truth that the access-control layer
 * consults whenever a student opens a paid {@code Lesson},
 * {@code LearningModule}, {@code CoursePackage}, or premium AI feature:
 * {@code EntitlementService.hasAccess} resolves to {@code true} when an
 * <em>active</em> row exists matching the current student and the
 * artefact identifier pair {@code (item_type, item_id)}.</p>
 *
 * <p>Rows are created by {@code PaymentService} when a {@link PaymentRecord}
 * transitions to {@link com.example.aichinese.entity.enums.PaymentStatus#PAID}
 * and are flipped to {@code active = false} when the order is refunded.
 * They are <em>not</em> deleted, so a refund-then-repurchase scenario
 * preserves a complete audit trail.</p>
 *
 * <p>The unique constraint on
 * {@code (order_id, item_type, item_id, feature_key_normalized)} prevents
 * Stripe webhook retries from inserting duplicate entitlements when a
 * single payment is delivered twice.
 * {@link #featureKeyNormalized} is a generated column populated by the
 * database (lower-cased {@code feature_key}, defaulting to {@code ""})
 * so {@code NULL}/empty feature keys still participate in the unique
 * index — Hibernate sees it as read-only via
 * {@code insertable = false, updatable = false}.</p>
 *
 * <p>{@link #version} guards against concurrent flips of {@link #active}
 * when a refund and a subsequent re-purchase race.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_entitlements", indexes = {
        @Index(name = "idx_payment_entitlements_student_id", columnList = "student_id"),
        @Index(name = "idx_payment_entitlements_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_entitlements_payment_id", columnList = "payment_record_id"),
        @Index(name = "idx_payment_entitlements_item", columnList = "item_type,item_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_entitlements_order_item_feature", columnNames = { "order_id", "item_type", "item_id", "feature_key_normalized" })
})
public class PaymentEntitlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_record_id", nullable = false)
    private PaymentRecord paymentRecord;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "feature_key", length = 100)
    private String featureKey;

    @Column(name = "feature_key_normalized", insertable = false, updatable = false, length = 100)
    private String featureKeyNormalized;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
