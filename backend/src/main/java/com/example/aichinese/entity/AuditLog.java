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
import lombok.Setter;

@Getter
@Setter
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
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actorUser;

    @Column(name = "actor_email", updatable = false, length = 150)
    private String actorEmail;

    @Column(name = "action_type", nullable = false, updatable = false, length = 100)
    private String actionType;

    @Column(name = "target_type", updatable = false, length = 100)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    /** What the entry is about in words (a person's name, a lesson title), as it was at the time. */
    @Column(name = "target_label", updatable = false, length = 255)
    private String targetLabel;

    /** The reason an admin gave for the decision, when one was required. */
    @Column(name = "reason", updatable = false, length = 1000)
    private String reason;

    @Lob
    @Column(name = "description", updatable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "request_id", updatable = false, length = 120)
    private String requestId;
}
