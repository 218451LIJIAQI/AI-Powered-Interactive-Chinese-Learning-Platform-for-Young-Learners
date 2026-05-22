package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "live_class_enrolments", indexes = {
        @Index(name = "idx_live_class_enrolments_live_class_id", columnList = "live_class_id"),
        @Index(name = "idx_live_class_enrolments_student_id", columnList = "student_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_live_class_enrolments_live_class_student", columnNames = { "live_class_id", "student_id" }),
        @UniqueConstraint(name = "uk_live_class_enrolments_active_student", columnNames = { "active_enrolment_key" })
})
public class LiveClassEnrolment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "live_class_id", nullable = false)
    private LiveClass liveClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrolment_status", nullable = false, length = 30)
    private LiveClassEnrolmentStatus enrolmentStatus = LiveClassEnrolmentStatus.ENROLLED;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    @Column(name = "active_enrolment_key", insertable = false, updatable = false, length = 220)
    private String activeEnrolmentKey;
}
