package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AccessType;
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

/**
 * Teacher-owned grouping of related {@link Lesson}s that share a
 * theme, level, or course week.
 *
 * <p>Modules sit between {@link CoursePackage}s and {@link Lesson}s in
 * the content hierarchy: a package may bundle modules <em>or</em>
 * directly bundle lessons (see {@link PackageItemType}), and each
 * lesson may belong to at most one module via {@link Lesson#getModule()}.</p>
 *
 * <p>The {@link #teacher} owner is responsible for the module's
 * editorial state. {@link #accessType} and {@link #price} apply when
 * the module is sold standalone (not as part of a package); a learner
 * with a parent-package entitlement automatically sees every
 * {@code FREE} or contained module without needing a separate
 * entitlement on the module itself.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_modules", indexes = {
        @Index(name = "idx_learning_modules_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_learning_modules_level_id", columnList = "level_id"),
        @Index(name = "idx_learning_modules_category_id", columnList = "category_id"),
        @Index(name = "idx_learning_modules_status", columnList = "content_status")
})
@NamedEntityGraph(
        // Single source of truth for the eager-fetch attribute set used
        // by every read on LearningModuleRepository: teacher dashboards,
        // public catalogue and admin moderation all need the joined
        // teacher / teacher.user / level / category rows hydrated in one
        // round trip. The teacher.user nested fetch is declared as a
        // sub-graph so the response mapper can render the teacher's
        // display name without an N+1 lazy load.
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
public class LearningModule extends BaseEntity {

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code LearningModule}: {@code teacher} (with the
     * nested {@code teacher.user} sub-graph), {@code level} and
     * {@code category}.
     */
    public static final String GRAPH_DETAIL = "LearningModule.detail";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType = AccessType.FREE;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
