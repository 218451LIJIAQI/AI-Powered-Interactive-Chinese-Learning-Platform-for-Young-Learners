package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription_plans", indexes = {
        @Index(name = "idx_subscription_plans_name", columnList = "name", unique = true)
})
public class SubscriptionPlan extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "MYR";

    @Lob
    @Column(name = "benefits", columnDefinition = "LONGTEXT")
    private String benefits;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
