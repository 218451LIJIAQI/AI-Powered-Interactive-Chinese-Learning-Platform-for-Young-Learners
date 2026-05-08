package com.example.aichinese.entity;

import java.math.BigDecimal;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_records", indexes = {
        @Index(name = "idx_payment_records_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_records_status", columnList = "payment_status")
})
public class PaymentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "MYR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
}
