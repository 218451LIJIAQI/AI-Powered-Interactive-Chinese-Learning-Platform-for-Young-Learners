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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refund_requests", indexes = {
        @Index(name = "idx_refund_requests_student_id", columnList = "student_id"),
        @Index(name = "idx_refund_requests_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_requests_status", columnList = "refund_status")
})
public class RefundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private RefundStatus refundStatus = RefundStatus.PENDING;
}
