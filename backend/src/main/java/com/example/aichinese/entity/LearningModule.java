package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.base.ReviewableContent;
import com.example.aichinese.entity.enums.ContentStatus;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_modules", indexes = {
        @Index(name = "idx_learning_modules_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_learning_modules_level_id", columnList = "level_id"),
        @Index(name = "idx_learning_modules_category_id", columnList = "category_id"),
        @Index(name = "idx_learning_modules_status", columnList = "content_status"),
        @Index(name = "idx_learning_modules_status_updated", columnList = "content_status,updated_at")
})
@NamedEntityGraph(
        name = LearningModule.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "LearningModule.teacherWithUser"),
                @NamedAttributeNode("level"),
                @NamedAttributeNode("category")
        },
        subgraphs = @NamedSubgraph(
                name = "LearningModule.teacherWithUser",
                attributeNodes = @NamedAttributeNode("user")
        )
)
public class LearningModule extends BaseEntity implements ReviewableContent {

    public static final String GRAPH_DETAIL = "LearningModule.detail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private LearningLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private LearningCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
