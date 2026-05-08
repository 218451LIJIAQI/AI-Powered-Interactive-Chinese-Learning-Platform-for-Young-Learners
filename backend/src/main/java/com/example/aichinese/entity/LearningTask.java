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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_tasks", indexes = {
        @Index(name = "idx_learning_tasks_student_id", columnList = "student_id"),
        @Index(name = "idx_learning_tasks_due_at", columnList = "due_at")
})
public class LearningTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "task_status", nullable = false, length = 30)
    private String taskStatus = "PENDING";

    @Column(name = "due_at")
    private LocalDateTime dueAt;
}
