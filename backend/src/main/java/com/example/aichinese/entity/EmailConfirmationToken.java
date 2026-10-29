package com.example.aichinese.entity;

import java.time.LocalDateTime;

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

import com.example.aichinese.entity.base.BaseEntity;

/**
 * A link that confirms a new account's e-mail address (only its SHA-256 hash is kept). It works once, for the
 * address it was sent to, until it expires; asking for a new link retires the older ones.
 */
@Getter
@Setter
@Entity
@Table(name = "email_confirmation_tokens", indexes = {
        @Index(name = "idx_email_confirmation_tokens_user", columnList = "user_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_confirmation_tokens_hash", columnNames = "token_hash")
})
public class EmailConfirmationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "email", nullable = false, updatable = false, length = 150)
    private String email;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
