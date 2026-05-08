package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "course_packages", indexes = {
        @Index(name = "idx_course_packages_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_course_packages_status", columnList = "content_status")
})
public class CoursePackage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private LearningLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private LearningCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType = AccessType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.HYBRID;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;
}
