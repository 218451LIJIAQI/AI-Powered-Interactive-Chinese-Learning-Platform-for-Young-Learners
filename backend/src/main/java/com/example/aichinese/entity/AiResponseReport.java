package com.example.aichinese.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AiFeatureKey;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_response_reports", indexes = {
        @Index(name = "idx_ai_response_reports_status_created_at", columnList = "status,created_at"),
        @Index(name = "idx_ai_response_reports_student_created", columnList = "student_user_id,created_at")
})
public class AiResponseReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false)
    private User studentUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, updatable = false, length = 40)
    private AiFeatureKey featureKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_category", nullable = false, updatable = false, length = 40)
    private AiResponseReportReason reasonCategory;

    @Column(name = "description", updatable = false, length = 1000)
    private String description;

    @Lob
    @Column(name = "prompt_snapshot", updatable = false, columnDefinition = "LONGTEXT")
    private String promptSnapshot;

    @Lob
    @Column(name = "ai_response_snapshot", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String aiResponseSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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
