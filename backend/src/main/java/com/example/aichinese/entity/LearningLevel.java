package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Learner proficiency level (e.g. "Beginner", "HSK 1", "HSK 2",
 * "Intermediate") referenced by both content artefacts and student
 * profiles.
 *
 * <p>{@link StudentProfile#getCurrentLevel()} tracks the learner's own
 * progression, while {@link Lesson#getLevel()},
 * {@link LearningModule#getLevel()}, and
 * {@link CoursePackage#getLevel()} declare the prerequisite level for a
 * piece of content. {@link #sortOrder} (ascending) is the canonical
 * progression order; the dashboard "next level" suggestion picks the
 * level immediately after the student's current one.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_levels", indexes = {
        @Index(name = "idx_learning_levels_name", columnList = "name", unique = true)
})
public class LearningLevel extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
