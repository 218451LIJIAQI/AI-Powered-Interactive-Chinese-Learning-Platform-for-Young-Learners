package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PracticeAiFeedbackStatus;
import com.example.aichinese.entity.enums.PracticeReviewStatus;
import com.example.aichinese.entity.enums.PracticeSubmissionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A learner's pronunciation recording or handwriting image kept for teacher review (D7, D7a,
 * FR-AIFB-05..07). The optional AI-assisted feedback is stored with it (never a score);
 * {@code ai_feedback_records} remains the AI log. An approved teacher who teaches the student
 * (roster) or owns the linked lesson can review it and link {@link TeacherFeedback} to it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practice_submissions", indexes = {
        @Index(name = "idx_practice_submissions_student_submitted", columnList = "student_id,submitted_at"),
        @Index(name = "idx_practice_submissions_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_practice_submissions_uploaded_file_id", columnList = "uploaded_file_id"),
        @Index(name = "idx_practice_submissions_review_status", columnList = "review_status,submitted_at")
})
public class PracticeSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_practice_submissions_student"))
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_practice_submissions_lesson"))
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, updatable = false, length = 30)
    private PracticeSubmissionType submissionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_file_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_practice_submissions_uploaded_file"))
    private UploadedFile uploadedFile;

    @Column(name = "target_text", length = 200, updatable = false)
    private String targetText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_feedback_status", nullable = false, length = 30)
    private PracticeAiFeedbackStatus aiFeedbackStatus = PracticeAiFeedbackStatus.UNAVAILABLE;

    @Lob
    @Column(name = "ai_feedback_text", columnDefinition = "LONGTEXT")
    private String aiFeedbackText;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private PracticeReviewStatus reviewStatus = PracticeReviewStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
