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

/**
 * Student-submitted complaint about a specific AI response.
 *
 * <p>This entity is the cornerstone of the platform's child-safety story:
 * any learner can flag a chat reply or generated artefact through
 * {@code POST /api/v1/student/ai-reports}, and an admin moderator works
 * the queue from the dashboard. The lifecycle follows
 * {@link com.example.aichinese.entity.enums.AiResponseReportStatus}:
 * {@code PENDING_REVIEW → IN_REVIEW → RESOLVED|DISMISSED}.</p>
 *
 * <h3>Immutable evidence snapshots</h3>
 * <p>{@link #promptSnapshot} and {@link #aiResponseSnapshot} are
 * deliberately stored as text rather than fetched live from the
 * underlying {@link AiChatMessage} / {@link AiUsageLog} so that:</p>
 * <ul>
 *   <li>Moderators see exactly what the student saw, even if the model
 *       output is regenerated or the chat session is later archived.</li>
 *   <li>Analytics on what triggers reports remain stable across upstream
 *       AI provider changes.</li>
 * </ul>
 *
 * <p>The optional {@link #aiUsageLog}, {@link #chatSession} and
 * {@link #chatMessage} relationships are kept nullable so a legacy log
 * without a session reference still produces a parseable report row;
 * deleting the underlying chat artefacts does not require cascading the
 * report.</p>
 */
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
        // Single source of truth for the eager-fetch attribute set used by
        // every read in AiResponseReportRepository: student moderation
        // pages render the full row in one round trip and need both
        // student/reviewer users plus the chat-link metadata. Keeping this
        // on the entity (instead of repeating the attribute path array
        // verbatim across seven derived methods) prevents the repository
        // and the entity from drifting apart whenever a new related field
        // is added — every consumer simply references the graph by name.
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

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches every
     * related row required to render the admin moderation list and the
     * student "my reports" view in a single SQL round trip.
     */
    public static final String GRAPH_ADMIN_DETAIL = "AiResponseReport.adminDetail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User studentUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_usage_log_id")
    private AiUsageLog aiUsageLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id")
    private AiChatSession chatSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    private AiChatMessage chatMessage;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", nullable = false, length = 40)
    private AiResponseReportReason reasonCategory;

    @Column(name = "description", length = 1000)
    private String description;

    @Lob
    @Column(name = "prompt_snapshot", columnDefinition = "LONGTEXT")
    private String promptSnapshot;

    @Lob
    @Column(name = "ai_response_snapshot", columnDefinition = "LONGTEXT")
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
