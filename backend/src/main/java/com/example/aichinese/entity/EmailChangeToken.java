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

/** A pending e-mail change: the new address takes effect when the link sent to it is opened (once, within 60 min). */
@Getter
@Setter
@Entity
@Table(name = "email_change_tokens", indexes = {
        @Index(name = "idx_email_change_tokens_user_id", columnList = "user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_change_tokens_token_hash", columnNames = "token_hash")
})
public class EmailChangeToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "new_email", nullable = false, updatable = false, length = 150)
    private String newEmail;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
