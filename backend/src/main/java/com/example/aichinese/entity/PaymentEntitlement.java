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
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_record_id", nullable = false, updatable = false)
    private PaymentRecord paymentRecord;

    @Column(name = "item_type", nullable = false, updatable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "feature_key", updatable = false, length = 100)
    private String featureKey;

    @Column(name = "feature_key_normalized", insertable = false, updatable = false, length = 100)
    private String featureKeyNormalized;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source_type", nullable = false, updatable = false, length = 50)
    private String sourceType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
