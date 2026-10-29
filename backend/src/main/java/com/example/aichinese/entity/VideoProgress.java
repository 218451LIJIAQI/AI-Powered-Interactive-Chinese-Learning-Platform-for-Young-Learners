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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "video_progress", indexes = {
        @Index(name = "idx_video_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_video_progress_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_video_progress_recorded_video_id", columnList = "recorded_video_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_video_progress_student_video", columnNames = { "student_id", "recorded_video_id" })
})
public class VideoProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_video_id", nullable = false, updatable = false)
    private RecordedLessonVideo recordedVideo;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;
}
