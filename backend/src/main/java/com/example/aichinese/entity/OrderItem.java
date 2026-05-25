package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single line of an {@link Order}, polymorphically referencing the
 * purchased artefact via {@link #itemType} and {@link #itemId}.
 *
 * <p>{@link #itemTitleSnapshot} captures the artefact's title at the
 * time of checkout so that historical receipts and refund forms keep
 * working even after the underlying lesson / package is renamed or
 * archived. The same pattern applies to {@link #unitPrice} and
 * {@link #lineTotal}: financial fields are immutable once written so
 * that audit trails and accounting reports remain stable.</p>
 *
 * <p>{@link #teacher} is the teacher who is entitled to commission for
 * this line, captured here (rather than chasing the FK on the lesson)
 * so that re-assigning a lesson to a new teacher does not retroactively
 * change historical earnings. The companion {@link TeacherEarning} row
 * is created from this attribution when the order moves to
 * {@link com.example.aichinese.entity.enums.PaymentStatus#PAID}.</p>
 *
 * <p>{@link #featureKey} is populated when {@link #itemType} is
 * {@code PREMIUM_AI} so the entitlement flow knows which AI feature
 * the subscription unlocks.</p>
 *
 * <p>The Javadoc contract above ("financial fields are immutable
 * once written") is enforced at the ORM layer: every domain field is
 * mapped {@code updatable = false}. The single legitimate write path
 * is {@code OrderService#createOrder}, which inserts each row exactly
 * once. Any later {@code entity.setX(\u2026)} + {@code save()} attempt is
 * silently dropped by Hibernate at flush time \u2014 receipts, refunds and
 * accounting exports therefore remain bit-for-bit reproducible.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_order_created", columnList = "order_id,created_at")
})
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "item_type", nullable = false, updatable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "item_title_snapshot", updatable = false, length = 200)
    private String itemTitleSnapshot;

    @Column(name = "feature_key", updatable = false, length = 100)
    private String featureKey;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", updatable = false)
    private TeacherProfile teacher;
}
