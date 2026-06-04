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

    public static final String GRAPH_DETAIL = "PaymentRecord.detail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "provider_name", nullable = false, updatable = false, length = 100)
    private String providerName;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

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
