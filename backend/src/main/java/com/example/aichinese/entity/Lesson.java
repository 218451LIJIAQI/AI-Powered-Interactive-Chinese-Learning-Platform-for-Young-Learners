package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.TeacherArchivableContent;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lessons_module_id", columnList = "module_id"),
        @Index(name = "idx_lessons_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_lessons_status", columnList = "content_status"),
        @Index(name = "idx_lessons_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_lessons_revision_of", columnList = "revision_of_lesson_id"),
        @Index(name = "idx_lessons_module_position", columnList = "module_id,unit_position")
})
@NamedEntityGraph(
        name = Lesson.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "Lesson.teacherWithUser"),
                @NamedAttributeNode("module"),
                @NamedAttributeNode("level"),
                @NamedAttributeNode("category")
        },
        subgraphs = @NamedSubgraph(
                name = "Lesson.teacherWithUser",
                attributeNodes = @NamedAttributeNode("user")
        )
)
public class Lesson extends TeacherArchivableContent {

    public static final String GRAPH_DETAIL = "Lesson.detail";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private LearningModule module;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private LearningCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private LearningLevel level;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.RECORDED;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    /** Order of the lesson inside its unit. */
    @Column(name = "unit_position", nullable = false)
    private Integer unitPosition = 0;

    /** When the lesson (or this revision) was last sent for review. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * Set on a working copy of a published lesson: the teacher edits the copy while students keep the published
     * lesson, and approval applies the copy to it (see LessonRevisionService). Never set on a lesson students see.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_of_lesson_id")
    private Lesson revisionOf;
}
