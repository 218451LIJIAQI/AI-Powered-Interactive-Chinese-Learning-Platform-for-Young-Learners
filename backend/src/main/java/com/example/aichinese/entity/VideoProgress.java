package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "video_progress", indexes = {
        @Index(name = "idx_video_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_video_progress_lesson_id", columnList = "lesson_id")
})
public class VideoProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "watched_seconds")
    private Integer watchedSeconds;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "completion_percent")
    private Integer completionPercent;
}
