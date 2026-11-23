package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PublishableContent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The quiz of a class lesson. */
@Getter
@Setter
@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_status_updated", columnList = "content_status,updated_at")
})
public class Quiz extends PublishableContent {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /**
     * "Allow SparkBot hints" of the lesson editor: learners stuck on a question can open its hint. Off for a new quiz.
     */
    @Column(name = "hints_allowed", nullable = false)
    private boolean hintsAllowed;

    /** On a quiz of a lesson revision: the published quiz it was copied from (null for a quiz new in the revision). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_source_id", foreignKey = @ForeignKey(name = "fk_quizzes_revision_source"))
    private Quiz revisionSource;

    /** Identifier of {@link #revisionSource}, readable from an uninitialized lazy proxy without hitting the database. */
    public Long getRevisionSourceId() {
        return revisionSource == null ? null : revisionSource.entityId();
    }
}
