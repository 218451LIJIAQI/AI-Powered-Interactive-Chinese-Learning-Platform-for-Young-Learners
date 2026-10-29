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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "live_class_enrolments", indexes = {
        @Index(name = "idx_live_class_enrolments_live_class_id", columnList = "live_class_id"),
        @Index(name = "idx_live_class_enrolments_student_id", columnList = "student_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_live_class_enrolments_active_student", columnNames = { "active_enrolment_key" })
})
public class LiveClassEnrolment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "live_class_id", nullable = false, updatable = false)
    private LiveClass liveClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
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

    // Database-generated, as in the V1 baseline, so the unique key above also works on a newly created H2
    // schema (demo and test profiles, a new desktop database). An existing desktop H2 file (ddl-auto: update)
    // keeps its old columns until its data folder is recreated. Flyway owns the MySQL column.
    // V1 tests "enrolment_status in ('ENROLLED','ATTENDED')"; LOCATE gives the same result without a
    // string comparison, which H2 2.4 can no longer evaluate once the connection that created the table is closed.
    @Column(name = "active_enrolment_key", insertable = false, updatable = false, length = 220,
            columnDefinition = "varchar(220) generated always as (case when locate(concat('|', enrolment_status, '|'),"
                    + " '|ENROLLED|ATTENDED|') > 0 then concat(live_class_id, '#', student_id) else null end)")
    private String activeEnrolmentKey;
}
