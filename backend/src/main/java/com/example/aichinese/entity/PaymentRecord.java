package com.example.aichinese.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PaymentStatus;

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
 * Single attempt by a payment provider (Stripe, manual, no-op mock) to
 * settle an {@link Order}.
 *
 * <p>An order may have several payment records over its lifetime — for
 * example a {@link com.example.aichinese.entity.enums.PaymentStatus#FAILED}
 * card decline followed by a successful {@link com.example.aichinese.entity.enums.PaymentStatus#PAID}
 * retry. The {@link Order#paymentStatus} mirrors only the most recent
 * <em>terminal</em> outcome and is updated by {@code PaymentService} as
 * webhook events flow in.</p>
 *
 * <p>The uniqueness pair {@code (provider_name, provider_reference)}
 * makes Stripe webhook delivery idempotent: re-receiving the same
 * {@code checkout.session.id} (or equivalent) is a no-op rather than
 * duplicating the row, and the cross-provider product-of-references
 * collision is avoided.</p>
 *
 * <p>{@link #version} is incremented on every state transition so that
 * the webhook handler and the synchronous polling fallback never both
 * succeed in writing inconsistent statuses.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_records", indexes = {
        @Index(name = "idx_payment_records_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_records_status", columnList = "payment_status"),
        @Index(name = "idx_payment_records_status_created", columnList = "payment_status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_records_provider_reference", columnNames = { "provider_name", "provider_reference" })
})
public class PaymentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "MYR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
