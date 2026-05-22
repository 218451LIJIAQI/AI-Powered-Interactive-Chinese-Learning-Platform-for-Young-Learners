package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PayoutStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payout_requests", indexes = {
        @Index(name = "idx_payout_requests_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_payout_requests_status", columnList = "payout_status")
})
public class PayoutRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "requested_note", length = 1000)
    private String requestedNote;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_status", nullable = false, length = 30)
    private PayoutStatus payoutStatus = PayoutStatus.PENDING;
}
