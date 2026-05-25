package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 *
 * <p>{@link #name} is unique to keep the level taxonomy free of
 * duplicates. Uniqueness is enforced by a single named constraint
 * ({@code idx_learning_levels_name}) declared via {@link UniqueConstraint}
 * on the {@link Table @Table} annotation, matching the Flyway baseline.
 * The historical {@code idx_} prefix is preserved to match the database,
 * even though {@code uk_} is the more conventional prefix for a unique
 * constraint.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "learning_levels",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_learning_levels_name", columnNames = "name")
        }
)
public class LearningLevel extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
