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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "points_transactions", indexes = {
        @Index(name = "idx_points_transactions_student_source_pair", columnList = "student_id,source_type,source_id"),
        @Index(name = "idx_points_transactions_created_at", columnList = "created_at"),
        @Index(name = "idx_points_transactions_student_created", columnList = "student_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_points_transactions_student_source", columnNames = { "student_id", "source_key" })
})
public class PointsTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "points_delta", nullable = false, updatable = false)
    private Integer pointsDelta;

    @Column(name = "reason", nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "source_type", updatable = false, length = 100)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    // Database-generated, as in the V1 baseline, so the unique key above also works on the schemas Hibernate
    // creates for the demo and test profiles. Flyway owns the column of the main database.
    @Column(name = "source_key", insertable = false, updatable = false, length = 220,
            columnDefinition = "varchar(220) generated always as (case when source_type is not null"
                    + " and source_id is not null then concat(source_type, '#', source_id) else null end) stored")
    private String sourceKey;
}
