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

/**
 * An invitation to become an admin. No account exists until the invitee opens the link and sets a password; the new
 * admin account is created, active, at that moment.
 */
@Getter
@Setter
@Entity
@Table(name = "admin_invites", indexes = {
        @Index(name = "idx_admin_invites_email", columnList = "email"),
        @Index(name = "idx_admin_invites_pending", columnList = "accepted_at,expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_invites_token_hash", columnNames = "token_hash")
})
public class AdminInvite extends BaseEntity {

    @Column(name = "email", nullable = false, updatable = false, length = 150)
    private String email;

    /** Replaced when the invite is sent again, so only the newest link works. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_user_id")
    private User acceptedUser;
}
