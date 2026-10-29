package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "teacher_feedback", indexes = {
        @Index(name = "idx_teacher_feedback_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_feedback_student_id", columnList = "student_id"),
        @Index(name = "idx_teacher_feedback_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_teacher_feedback_quiz_attempt_id", columnList = "quiz_attempt_id"),
        @Index(name = "idx_teacher_feedback_class", columnList = "course_package_id")
})
@NamedEntityGraph(
        name = TeacherFeedback.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "TeacherFeedback.teacherWithUser"),
                @NamedAttributeNode(value = "student", subgraph = "TeacherFeedback.studentWithUser"),
                @NamedAttributeNode("lesson"),
                @NamedAttributeNode(value = "quizAttempt", subgraph = "TeacherFeedback.quizAttemptChain")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "TeacherFeedback.teacherWithUser",
                        attributeNodes = @NamedAttributeNode("user")),
                @NamedSubgraph(
                        name = "TeacherFeedback.studentWithUser",
                        attributeNodes = @NamedAttributeNode("user")),
                @NamedSubgraph(
                        name = "TeacherFeedback.quizAttemptChain",
                        attributeNodes = {
                                @NamedAttributeNode(value = "quiz", subgraph = "TeacherFeedback.quizWithLesson")
                        }),
                @NamedSubgraph(
                        name = "TeacherFeedback.quizWithLesson",
                        attributeNodes = @NamedAttributeNode("lesson"))
        }
)
public class TeacherFeedback extends BaseEntity {

    public static final String GRAPH_DETAIL = "TeacherFeedback.detail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_attempt_id", updatable = false)
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false)
    private Lesson lesson;

    @Column(name = "feedback_type", length = 50)
    private String feedbackType;

    @Lob
    @Column(name = "feedback_message", nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackMessage;

    /** The class the student sees the feedback in (its Feedback tab). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_package_id", updatable = false)
    private CoursePackage coursePackage;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;
}
