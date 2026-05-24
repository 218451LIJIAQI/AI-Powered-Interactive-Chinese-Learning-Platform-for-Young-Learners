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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Teacher-specific extension of {@link User}.
 *
 * <p>Verification status ({@link #teacherStatus}) is the
 * authorisation gate enforced by
 * {@code TeacherContentAccessService.requireApprovedTeacher}: only
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * teachers may publish content, schedule live classes, or be
 * surfaced in the public teacher catalogue.</p>
 *
 * <p>The verification trail flows through:</p>
 * <ul>
 *   <li>{@link TeacherVerificationDocument} — uploaded credentials</li>
 *   <li>{@link TeacherVerificationReview} — admin review decisions</li>
 * </ul>
 *
 * <p>{@link #publicVisible} is a teacher-controlled visibility toggle
 * applied <em>after</em> verification: an approved teacher can still
 * temporarily hide their public profile (e.g. while on leave) without
 * losing their approved status.</p>
 *
 * <p>{@link #rejectionReason} is populated when the most recent
 * verification review is {@code REJECTED} and is surfaced back to the
 * teacher so they know what to correct on resubmission.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_profiles", indexes = {
        @Index(name = "idx_teacher_profiles_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_teacher_profiles_status", columnList = "teacher_status"),
        @Index(name = "idx_teacher_profiles_status_created", columnList = "teacher_status,created_at")
})
public class TeacherProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_status", nullable = false, length = 30)
    private TeacherStatus teacherStatus = TeacherStatus.PENDING;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "qualification_summary", length = 1000)
    private String qualificationSummary;

    @Column(name = "teaching_categories", length = 500)
    private String teachingCategories;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible;
}
