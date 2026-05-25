package com.example.aichinese.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared per-user AI quota state.
 *
 * <p>This row is deliberately database-backed instead of in-memory so two
 * backend instances cannot each grant their own minute/day allowance. The
 * limiter locks the owning {@link User} row first, then updates this counter in
 * a short transaction before the provider HTTP call starts.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "ai_usage_quota_counters",
        indexes = {
                @Index(name = "idx_ai_usage_quota_counters_user_id", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_usage_quota_counters_user", columnNames = "user_id")
        })
public class AiUsageQuotaCounter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "minute_started_at", nullable = false)
    private LocalDateTime minuteStartedAt;

    @Column(name = "minute_count", nullable = false)
    private int minuteCount;

    @Column(name = "usage_day", nullable = false)
    private LocalDate usageDay;

    @Column(name = "day_count", nullable = false)
    private int dayCount;
}
