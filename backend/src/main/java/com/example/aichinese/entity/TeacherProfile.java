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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "teacher_profiles",
        indexes = {
                @Index(name = "idx_teacher_profiles_status", columnList = "teacher_status"),
                @Index(name = "idx_teacher_profiles_status_created", columnList = "teacher_status,created_at"),
                @Index(name = "idx_teacher_profiles_status_submitted",
                        columnList = "teacher_status,verification_submitted_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_teacher_profiles_user_id", columnNames = "user_id")
        }
)
public class TeacherProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_status", nullable = false, length = 30)
    private TeacherStatus teacherStatus = TeacherStatus.PENDING;

    @Column(name = "bio", length = 2000)
    private String bio;

    /**
     * No longer edited (like {@code teachingCategories}); still mapped so that account deletion clears what older
     * versions of the profile form stored.
     */
    @Column(name = "qualification_summary", length = 1000)
    private String qualificationSummary;

    @Column(name = "teaching_categories", length = 500)
    private String teachingCategories;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** Not used: kept only because the column is NOT NULL without a default (who is shown: ContentVisibilityPolicy). */
    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible;

    /** When the teacher last sent the application to the admins; null until the first submission. */
    @Column(name = "verification_submitted_at")
    private LocalDateTime verificationSubmittedAt;

    /** When the teacher agreed to follow MandarinSpark's expectations of teachers (recorded on submission). */
    @Column(name = "standards_accepted_at")
    private LocalDateTime standardsAcceptedAt;
}
