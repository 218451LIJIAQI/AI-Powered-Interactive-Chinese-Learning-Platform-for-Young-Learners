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
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resume-position bookmark for a single student watching a single
 * {@link RecordedLessonVideo}.
 *
 * <p>The unique constraint on {@code (student_id, recorded_video_id)}
 * means each student keeps exactly one bookmark per video — re-opening
 * the same video resumes from {@link #watchedSeconds} rather than the
 * start.</p>
 *
 * <p>{@link #completed} is set {@code true} the first time the watched
 * percentage crosses a threshold (typically 95% to tolerate trailing
 * credits), and stays {@code true} on subsequent rewatches so the
 * lesson's overall completion roll-up does not regress.</p>
 *
 * <p>{@link #version} prevents the lossy interleaving of frequent
 * progress pings during active playback.</p>
 */
@Getter
@Setter
@NoArgsConstructor
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
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_video_id", nullable = false)
    private RecordedLessonVideo recordedVideo;

    @Column(name = "watched_seconds")
    private Integer watchedSeconds;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "total_seconds")
    private Integer totalSeconds;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;
}
