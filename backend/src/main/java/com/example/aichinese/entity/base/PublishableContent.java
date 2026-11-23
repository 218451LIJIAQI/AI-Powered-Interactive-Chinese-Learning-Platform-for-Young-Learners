package com.example.aichinese.entity.base;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Titled content an admin publishes (FRCONT07, FRADM03): classes, units, lessons, quizzes and the six
 * practice-activity types. Learners see only published content.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class PublishableContent extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;
}
