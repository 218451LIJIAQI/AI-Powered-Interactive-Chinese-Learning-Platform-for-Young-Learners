package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tamper-evident audit log entry for security- and compliance-relevant
 * events across the platform.
 *
 * <p>Written by {@code AuditLogService} from auth (login, logout,
 * password reset), commerce (refund, payout decision), moderation,
 * and admin-action paths. Rows are append-only — the service offers no
 * update or delete API.</p>
 *
 * <p>{@link #actorEmail} is denormalised alongside {@link #actorUser}
 * so the audit trail keeps an interpretable identifier even after the
 * actor is deleted or renamed.</p>
 *
 * <p>{@link #targetType} / {@link #targetId} polymorphically reference
 * the affected resource without forcing a JPA association — entity
 * deletes do not cascade and do not weaken the audit trail.</p>
 *
 * <p>{@link #ipAddress} and {@link #requestId} (the per-request
 * correlation id sourced from {@code RequestIdHolder}) capture the
 * network and trace context for forensic analysis. The
 * {@code AuditLogService} pre-redacts authorisation tokens and bearer
 * values from {@link #description} before persisting.</p>
 *
 * <p>The compound indexes back the typical "search by actor and time
 * window" admin queries plus the "all events on this resource"
 * forensic query.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_actor_user_id", columnList = "actor_user_id"),
        @Index(name = "idx_audit_logs_action_type", columnList = "action_type"),
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_target_created", columnList = "target_type,target_id,created_at"),
        @Index(name = "idx_audit_logs_actor_created", columnList = "actor_user_id,created_at"),
        @Index(name = "idx_audit_logs_actor_email_created", columnList = "actor_email,created_at")
})
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Lob
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "request_id", length = 120)
    private String requestId;
}
