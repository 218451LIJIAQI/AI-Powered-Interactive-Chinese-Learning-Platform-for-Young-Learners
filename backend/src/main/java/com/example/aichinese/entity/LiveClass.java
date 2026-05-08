package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.LiveClassStatus;

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
@Table(name = "live_classes", indexes = {
        @Index(name = "idx_live_classes_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_live_classes_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_live_classes_status", columnList = "live_class_status"),
        @Index(name = "idx_live_classes_scheduled_start_at", columnList = "scheduled_start_at")
})
public class LiveClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private LocalDateTime scheduledEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "live_class_status", nullable = false, length = 30)
    private LiveClassStatus liveClassStatus = LiveClassStatus.SCHEDULED;
}
