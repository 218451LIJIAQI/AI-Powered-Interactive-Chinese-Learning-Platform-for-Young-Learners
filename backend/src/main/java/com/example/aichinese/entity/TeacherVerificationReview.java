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

/**
 * Single admin decision taken against a teacher's verification
 * submission.
 *
 * <p>One review may apply to a whole round of submitted
 * {@link TeacherVerificationDocument}s (typical case) or be scoped to
 * a specific document via {@link #document}. The
 * {@link #decisionStatus} mirrors the canonical
 * {@link com.example.aichinese.entity.enums.TeacherStatus} so the
 * admin's verdict directly drives the
 * {@link TeacherProfile#getTeacherStatus()} field.</p>
 *
 * <p>The full audit trail of all reviews per teacher is preserved (no
 * soft-delete), with the latest decision indexed for efficient
 * retrieval via the {@code idx_teacher_verification_reviews_latest}
 * index.</p>
 *
 * <p>{@link #reviewerUser} is nullable to support legacy data; the
 * application layer requires it for new reviews.</p>
 *
 * <p>This table is append-only: every field is mapped
 * {@code updatable = false}. The Javadoc above ("full audit trail
 * preserved, no soft-delete") describes the intent; the
 * {@code updatable} flags here make the contract technical. Reversing
 * a decision is expressed by inserting a fresh review row with the new
 * status, never by mutating the historical decision in place — which
 * would erase the very audit trail this table exists to preserve.</p>
 */
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
}
