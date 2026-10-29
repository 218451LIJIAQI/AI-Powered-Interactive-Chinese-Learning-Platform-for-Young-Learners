package com.example.aichinese.entity;

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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "idx_password_reset_tokens_token", columnNames = "token"),
        @UniqueConstraint(name = "uk_password_reset_tokens_active_user", columnNames = { "active_token_user_id" })
})
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token", nullable = false, updatable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // Database-generated, as in the V1 baseline, so the unique key above also works on a newly created H2
    // schema (demo and test profiles, a new desktop database). An existing desktop H2 file (ddl-auto: update)
    // keeps its old columns until its data folder is recreated. Flyway owns the MySQL column.
    @Column(name = "active_token_user_id", insertable = false, updatable = false,
            columnDefinition = "bigint generated always as (case when used_at is null then user_id else null end)")
    private Long activeTokenUserId;
}
