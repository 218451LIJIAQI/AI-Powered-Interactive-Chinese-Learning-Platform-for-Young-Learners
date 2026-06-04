package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.TeacherStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_verification_reviews", indexes = {
        @Index(name = "idx_teacher_verification_reviews_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_verification_reviews_reviewer_user_id", columnList = "reviewer_user_id"),
        @Index(name = "idx_teacher_verification_reviews_latest", columnList = "teacher_id,reviewed_at,id")
})
public class TeacherVerificationReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", updatable = false)
    private TeacherVerificationDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id", updatable = false)
    private User reviewerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, updatable = false, length = 30)
    private TeacherStatus decisionStatus;

    @Column(name = "review_note", updatable = false, length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private LocalDateTime reviewedAt;

    public void setDecisionStatus(TeacherStatus decisionStatus) {
        if (decisionStatus == null) {
            throw new IllegalArgumentException("Teacher verification review decision status is required.");
        }
        if (decisionStatus == TeacherStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Teacher verification review decision status must be APPROVED or REJECTED.");
        }
        this.decisionStatus = decisionStatus;
    }
}
