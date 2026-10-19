package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.base.ReviewableContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lessons_module_id", columnList = "module_id"),
        @Index(name = "idx_lessons_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_lessons_status", columnList = "content_status"),
        @Index(name = "idx_lessons_status_updated", columnList = "content_status,updated_at")
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
public class Lesson extends BaseEntity implements ReviewableContent {

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

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Lob
    @Column(name = "lesson_content", columnDefinition = "LONGTEXT")
    private String lessonContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.RECORDED;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;
}
