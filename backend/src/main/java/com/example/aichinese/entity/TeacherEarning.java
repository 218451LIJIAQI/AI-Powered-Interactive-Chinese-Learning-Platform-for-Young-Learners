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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_earnings", indexes = {
        @Index(name = "idx_teacher_earnings_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_earnings_order_item_id", columnList = "order_item_id")
})
public class TeacherEarning extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(name = "gross_amount", precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", precision = 10, scale = 2)
    private BigDecimal netAmount;
}
