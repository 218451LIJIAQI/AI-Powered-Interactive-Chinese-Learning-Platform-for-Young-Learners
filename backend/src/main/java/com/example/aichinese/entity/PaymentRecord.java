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
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
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
@NamedEntityGraph(
        // Single source of truth for the eager-fetch attribute set used
        // by every read in PaymentRecordRepository: the student / admin
        // payment history views and the refund flow all need order +
        // student + student.user hydrated in one round trip so the
        // response mapper does not regress into per-row lazy lookups.
        name = PaymentRecord.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode("order"),
                @NamedAttributeNode(value = "student", subgraph = "PaymentRecord.studentWithUser")
        },
        subgraphs = @NamedSubgraph(
                name = "PaymentRecord.studentWithUser",
                attributeNodes = @NamedAttributeNode("user")
        )
)
public class PaymentRecord extends BaseEntity {

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code PaymentRecord}: {@code order} and {@code student}
     * with its nested {@code student.user} sub-graph.
     */
    public static final String GRAPH_DETAIL = "PaymentRecord.detail";

    /**
     * Owning order. Mapped {@code updatable = false}: a payment record
     * is bound to the order it was issued for and cannot be re-targeted.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    /**
     * Buyer. Mapped {@code updatable = false}: a payment record always
     * settles against the original buyer, never a different one.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    /**
     * Provider identifier (e.g. {@code "stripe"}, {@code "mock"},
     * {@code "no-op"}). Mapped {@code updatable = false}: switching the
     * provider on an existing record would break webhook routing.
     */
    @Column(name = "provider_name", nullable = false, updatable = false, length = 100)
    private String providerName;

    /**
     * Provider-side session/payment reference (e.g. Stripe
     * {@code checkout.session.id}). Filled by
     * {@code PaymentService.startCheckout} <em>after</em> the initial
     * insert, so this field MUST remain mutable; it then settles as the
     * effective idempotency key together with {@link #providerName}.
     */
    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    /**
     * Amount captured at provider-session creation time. Mapped
     * {@code updatable = false}: refunds are tracked as separate
     * {@code RefundRequest} rows rather than rewriting historical
     * payment amounts; accounting exports therefore stay reproducible.
     */
    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * ISO-4217 currency code captured at payment-creation time.
     * Mapped {@code updatable = false} for the same reproducibility
     * reason as {@link #amount}.
     */
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
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
