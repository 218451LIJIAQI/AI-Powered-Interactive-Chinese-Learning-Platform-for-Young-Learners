package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PublishableContent;
import com.example.aichinese.entity.enums.ContentStatus;

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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lessons_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_lessons_module_position", columnList = "module_id,unit_position")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_lessons_revision_of", columnNames = "revision_of_lesson_id")
})
public class Lesson extends PublishableContent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private LearningModule module;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    /** Order of the lesson inside its unit. */
    @Column(name = "unit_position", nullable = false)
    private Integer unitPosition = 0;

    /** Status before the lesson was archived, so a restore returns it there; null while not archived. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_archive", length = 30)
    private ContentStatus statusBeforeArchive;

    /**
     * Set on the working copy of a published lesson: an admin edits the copy while learners keep the published lesson,
     * and publishing applies the copy to it (see LessonRevisionService). Never set on a lesson learners see.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_of_lesson_id")
    private Lesson revisionOf;
}
