package com.example.aichinese.entity.base;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Content that goes through the teacher-submit / admin-review workflow (FRCONT07, FRADM03):
 * courses, modules, lessons, quizzes and the six practice-activity types.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class ReviewableContent extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
