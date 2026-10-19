package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
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
        @Index(name = "idx_teacher_feedback_quiz_attempt_id", columnList = "quiz_attempt_id"),
        @Index(name = "idx_teacher_feedback_practice_submission_id", columnList = "practice_submission_id")
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

    /** Optional link to the reviewed pronunciation/handwriting practice submission (D7a). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_submission_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_teacher_feedback_practice_submission"))
    private PracticeSubmission practiceSubmission;

    @Column(name = "feedback_type", length = 50)
    private String feedbackType;

    @Lob
    @Column(name = "feedback_message", nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackMessage;
}
