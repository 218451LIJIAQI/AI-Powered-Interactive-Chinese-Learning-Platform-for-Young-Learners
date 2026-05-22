package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_progress", indexes = {
        @Index(name = "idx_student_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_student_progress_level_id", columnList = "level_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_progress_student", columnNames = { "student_id" })
})
public class StudentProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private LearningLevel level;

    @Column(name = "completion_percent")
    private Integer completionPercent;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "weak_areas", length = 500)
    private String weakAreas;

    @Column(name = "last_activity_summary", length = 500)
    private String lastActivitySummary;
}
