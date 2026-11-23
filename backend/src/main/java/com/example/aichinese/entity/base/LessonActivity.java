package com.example.aichinese.entity.base;

import com.example.aichinese.entity.Lesson;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * A practice activity type that a class lesson can hold (flashcards, character tracing or matching). The lesson's
 * activities are listed in their type's fixed slot order. An activity of a lesson revision remembers the published
 * activity it was copied from, so publishing updates that activity and preserves students' completions.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class LessonActivity extends PracticeActivityContent {

    /** The class lesson this activity belongs to; null for a practice-library set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "lesson_position")
    private Integer lessonPosition;

    @Column(name = "revision_source_id")
    private Long revisionSourceId;
}
