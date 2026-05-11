package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ReportStatus;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_reporter_id", columnList = "reporter_id"),
        @Index(name = "idx_reports_status", columnList = "report_status")
})
public class Report extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Lob
    @Column(name = "reason", nullable = false, columnDefinition = "LONGTEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 30)
    private ReportStatus reportStatus = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_teacher_id")
    private TeacherProfile relatedTeacher;

    @Lob
    @Column(name = "admin_note", columnDefinition = "LONGTEXT")
    private String adminNote;

    @Column(name = "teacher_revision_requested", nullable = false)
    private boolean teacherRevisionRequested;
}
