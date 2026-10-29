package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.TeacherArchivableContent;
import com.example.aichinese.entity.enums.ClassRevisionStatus;
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
@Table(name = "course_packages", indexes = {
        @Index(name = "idx_course_packages_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_course_packages_status", columnList = "content_status"),
        @Index(name = "idx_course_packages_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_course_packages_revision_status", columnList = "revision_status")
})
@NamedEntityGraph(
        name = CoursePackage.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "CoursePackage.teacherWithUser"),
                @NamedAttributeNode("level"),
                @NamedAttributeNode("category")
        },
        subgraphs = @NamedSubgraph(
                name = "CoursePackage.teacherWithUser",
                attributeNodes = @NamedAttributeNode("user")
        )
)
public class CoursePackage extends TeacherArchivableContent {

    public static final String GRAPH_DETAIL = "CoursePackage.detail";

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

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.HYBRID;

    /** Short note from the teacher on the class page ("Note from Li Wei"); shown to joined students only. */
    @Column(name = "teacher_note", length = 500)
    private String teacherNote;

    /** When the class was last sent for review (oldest first in the admin review queue). */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // Pending change to the details of an approved class: students keep the approved details until approval.

    @Column(name = "pending_title", length = 200)
    private String pendingTitle;

    @Column(name = "pending_description", length = 2000)
    private String pendingDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_level_id")
    private LearningLevel pendingLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_category_id")
    private LearningCategory pendingCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_learning_type", length = 20)
    private LearningType pendingLearningType;

    /** Null while the approved details have no pending change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "revision_status", length = 20)
    private ClassRevisionStatus revisionStatus;

    @Column(name = "revision_rejection_reason", length = 1000)
    private String revisionRejectionReason;

    @Column(name = "revision_submitted_at")
    private LocalDateTime revisionSubmittedAt;
}
