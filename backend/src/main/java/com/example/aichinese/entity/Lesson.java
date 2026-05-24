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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atomic teaching unit — the smallest piece of content a learner
 * actually consumes.
 *
 * <p>A lesson can stand alone or live inside a {@link LearningModule},
 * be sold individually, or be bundled into a {@link CoursePackage}.
 * Companion entities attach the actual delivery payload:</p>
 * <ul>
 *   <li>{@link RecordedLessonVideo} — for {@link com.example.aichinese.entity.enums.LearningType#RECORDED}
 *       and {@link com.example.aichinese.entity.enums.LearningType#HYBRID} lessons.</li>
 *   <li>{@link LiveClass} — for {@link com.example.aichinese.entity.enums.LearningType#LIVE}
 *       and {@code HYBRID} lessons.</li>
 *   <li>{@link Quiz} — assessment attached to the lesson.</li>
 *   <li>Activity entities (drag-drop, flashcards, etc.) — referenced via
 *       intermediate join tables, not direct FKs.</li>
 * </ul>
 *
 * <p>{@link #lessonContent} is the rich-text body shown above the
 * media; persisted as {@code LONGTEXT} to accommodate teacher-authored
 * long-form material.</p>
 *
 * <p>{@link #estimatedDurationMinutes} drives the catalogue badge and
 * the student dashboard "study plan" estimator; it is a hint, not an
 * enforced timer.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lessons_module_id", columnList = "module_id"),
        @Index(name = "idx_lessons_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_lessons_status", columnList = "content_status")
})
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private LearningModule module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
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
    @Column(name = "access_type", nullable = false, length = 20)
    private AccessType accessType = AccessType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_type", nullable = false, length = 20)
    private LearningType learningType = LearningType.RECORDED;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;
}
