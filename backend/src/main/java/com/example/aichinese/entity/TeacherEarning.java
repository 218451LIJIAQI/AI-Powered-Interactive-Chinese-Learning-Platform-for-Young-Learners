package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.TeacherEarningStatus;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_earnings", indexes = {
        @Index(name = "idx_teacher_earnings_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_earnings_order_item_id", columnList = "order_item_id"),
        @Index(name = "idx_teacher_earnings_teacher_status_created", columnList = "teacher_id,earning_status,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_earnings_order_item", columnNames = { "order_item_id" })
})
public class TeacherEarning extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_status", nullable = false, length = 30)
    private TeacherEarningStatus earningStatus = TeacherEarningStatus.AVAILABLE;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
