package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
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
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Top-level commerce bundle that aggregates {@link Lesson}s and
 * {@link LearningModule}s under one purchase point.
 *
 * <p>The composition is materialised through {@link PackageItem} rows
 * (rather than a JPA collection) so that adding a lesson to a package
 * does not require re-saving the package aggregate, and so that the
 * order of items inside the package is independent of the lesson's
 * position inside its module.</p>
 *
 * <p>{@link #accessType} is the platform's primary access tier:</p>
 * <ul>
 *   <li>{@link com.example.aichinese.entity.enums.AccessType#FREE} — visible
 *       to every authenticated student without a {@link PaymentEntitlement}.</li>
 *   <li>{@link com.example.aichinese.entity.enums.AccessType#PAID} — gated
 *       on an active entitlement; {@link #price} must be set.</li>
 * </ul>
 *
 * <p>{@link #learningType} hints at the delivery model (live, recorded,
 * hybrid) and is surfaced in the catalogue UI; the actual delivery is
 * driven by the per-lesson learning type.</p>
 *
 * <p>{@link #contentStatus} drives both author-side editing rules and
 * public-catalogue visibility; teacher-owned packages bypass the legacy
 * admin-review flow and go directly to {@code PUBLISHED}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "course_packages", indexes = {
        @Index(name = "idx_course_packages_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_course_packages_status", columnList = "content_status")
})
@NamedEntityGraph(
        // Single source of truth for the eager-fetch attribute set used by
        // every read in CoursePackageRepository: catalogue listings,
        // teacher dashboards and admin moderation all need the joined
        // teacher / teacher.user / level / category rows in one round trip.
        // Centralising the graph avoids the previous eight-fold
        // duplication of attributePaths arrays across the repository.
        // The teacher.user path is declared as a sub-graph so the mapper
        // can render the teacher's display name without an N+1 lazy load.
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
public class CoursePackage extends BaseEntity {

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code CoursePackage}: {@code teacher} (with the nested
     * {@code teacher.user} sub-graph), {@code level} and
     * {@code category}.
     */
    public static final String GRAPH_DETAIL = "CoursePackage.detail";

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
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType = AccessType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.HYBRID;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
}
