package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_response_reports", indexes = {
        @Index(name = "idx_ai_response_reports_student_user_id", columnList = "student_user_id"),
        @Index(name = "idx_ai_response_reports_status", columnList = "status"),
        @Index(name = "idx_ai_response_reports_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_response_reports_usage_log_id", columnList = "ai_usage_log_id"),
        @Index(name = "idx_ai_response_reports_chat_session_id", columnList = "chat_session_id"),
        @Index(name = "idx_ai_response_reports_student_created", columnList = "student_user_id,created_at")
})
@NamedEntityGraph(
        name = AiResponseReport.GRAPH_ADMIN_DETAIL,
        attributeNodes = {
                @NamedAttributeNode("studentUser"),
                @NamedAttributeNode("reviewerUser"),
                @NamedAttributeNode("aiUsageLog"),
                @NamedAttributeNode("chatSession"),
                @NamedAttributeNode("chatMessage")
        }
)
public class AiResponseReport extends BaseEntity {

    public static final String GRAPH_ADMIN_DETAIL = "AiResponseReport.adminDetail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false)
    private User studentUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_usage_log_id", updatable = false)
    private AiUsageLog aiUsageLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", updatable = false)
    private AiChatSession chatSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id", updatable = false)
    private AiChatMessage chatMessage;

    @Column(name = "feature_key", nullable = false, updatable = false, length = 100)
    private String featureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", nullable = false, updatable = false, length = 40)
    private AiResponseReportReason reasonCategory;

    @Column(name = "description", updatable = false, length = 1000)
    private String description;

    @Lob
    @Column(name = "prompt_snapshot", updatable = false, columnDefinition = "LONGTEXT")
    private String promptSnapshot;

    @Lob
    @Column(name = "ai_response_snapshot", updatable = false, columnDefinition = "LONGTEXT")
    private String aiResponseSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AiResponseReportStatus status = AiResponseReportStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewerUser;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
