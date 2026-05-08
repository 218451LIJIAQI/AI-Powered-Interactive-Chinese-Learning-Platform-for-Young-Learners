package com.example.aichinese.entity;

import java.time.LocalDateTime;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_badges", indexes = {
        @Index(name = "idx_student_badges_student_id", columnList = "student_id"),
        @Index(name = "idx_student_badges_badge_id", columnList = "badge_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_badges_student_badge", columnNames = { "student_id", "badge_id" })
})
public class StudentBadge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false)
    private LocalDateTime awardedAt = LocalDateTime.now();
}
