package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ClassContentEventType;

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
import lombok.Setter;

/**
 * One step in the history of a class and its lessons: created, submitted, approved, changes requested (with the
 * reason in {@link #note}), archived or restored. A submitted revision keeps its change list in {@link #changes}
 * (JSON), so the history can say what changed after the revision was applied.
 */
@Getter
@Setter
@Entity
@Table(name = "class_content_events", indexes = {
        @Index(name = "idx_class_content_events_class", columnList = "course_package_id,created_at"),
        @Index(name = "idx_class_content_events_lesson", columnList = "lesson_id,created_at")
})
public class ClassContentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_package_id", nullable = false, updatable = false)
    private CoursePackage coursePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private ClassContentEventType eventType;

    /** Title of the class or lesson when it happened. */
    @Column(name = "subject_title", nullable = false, updatable = false, length = 200)
    private String subjectTitle;

    @Column(name = "note", updatable = false, length = 1000)
    private String note;

    @Lob
    @Column(name = "changes", updatable = false, columnDefinition = "LONGTEXT")
    private String changes;
}
