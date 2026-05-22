package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_feedback", indexes = {
        @Index(name = "idx_teacher_feedback_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_feedback_student_id", columnList = "student_id"),
        @Index(name = "idx_teacher_feedback_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_teacher_feedback_quiz_attempt_id", columnList = "quiz_attempt_id")
})
public class TeacherFeedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_attempt_id")
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "feedback_type", length = 50)
    private String feedbackType;

    @Lob
    @Column(name = "feedback_message", nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackMessage;
}
