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
import lombok.Setter;

/** A student joined a teacher's class (every class is free); it gives access to the class lessons. */
@Getter
@Setter
@Entity
@Table(name = "class_enrolments", indexes = {
        @Index(name = "idx_class_enrolments_class_id", columnList = "course_package_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_class_enrolments_student_class", columnNames = { "student_id", "course_package_id" })
})
public class ClassEnrolment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_package_id", nullable = false, updatable = false)
    private CoursePackage coursePackage;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
