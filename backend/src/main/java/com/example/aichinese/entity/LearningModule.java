package com.example.aichinese.entity;

import com.example.aichinese.entity.base.TeacherArchivableContent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class LearningModule extends TeacherArchivableContent {

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

    @Column(name = "description", length = 2000)
    private String description;
}
