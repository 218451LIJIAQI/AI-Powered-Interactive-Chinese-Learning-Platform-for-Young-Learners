package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;

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
import lombok.Getter;
import lombok.Setter;

/** A Help & support request (#1000 upwards) from a student, a teacher or a visitor, answered in the admin queue. */
@Getter
@Setter
@Entity
@Table(name = "support_requests", indexes = {
        @Index(name = "idx_support_requests_requester_created", columnList = "requester_user_id,created_at"),
        @Index(name = "idx_support_requests_status_created", columnList = "request_status,created_at"),
        @Index(name = "idx_support_requests_status_closes", columnList = "request_status,closes_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_support_requests_number", columnNames = "request_number")
})
public class SupportRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, updatable = false)
    private Integer number;

    /** The signed-in requester; null for a visitor (and after the requester deleted the account). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requester;

    /** Where a visitor's replies are e-mailed and what support sees; a snapshot taken when the request was sent. */
    @Column(name = "requester_email", nullable = false, length = 150)
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_role", nullable = false, updatable = false, length = 20)
    private SupportRequesterRole requesterRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_topic", nullable = false, updatable = false, length = 20)
    private SupportTopic topic;

    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private SupportRequestStatus status = SupportRequestStatus.OPEN;

    @Column(name = "last_support_reply_at")
    private LocalDateTime lastSupportReplyAt;

    /** While ANSWERED: when the request closes by itself if the requester does not reply. */
    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "solved_at")
    private LocalDateTime solvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by", length = 20)
    private SupportClosedBy closedBy;
}
