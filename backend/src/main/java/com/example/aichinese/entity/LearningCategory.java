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
 * Curated topical category (e.g. "Pinyin", "HSK 1 Vocabulary",
 * "Chinese Festivals") used to facet the public catalogue.
 *
 * <p>Categories are purely metadata; they do <em>not</em> grant access
 * or define a content hierarchy. The student-facing catalogue groups
 * lessons, modules, and packages by their assigned category and orders
 * the categories themselves by {@link #sortOrder} (ascending).</p>
 *
 * <p>{@link #name} is unique to keep the public catalogue free of
 * confusing duplicates. Uniqueness is enforced by a single named
 * constraint ({@code idx_learning_categories_name}) declared via
 * {@link UniqueConstraint} on the {@link Table @Table} annotation,
 * matching the Flyway baseline. The historical {@code idx_} prefix
 * is preserved to match the database, even though {@code uk_} is the
 * more conventional prefix for a unique constraint.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "learning_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_learning_categories_name", columnNames = "name")
        }
)
public class LearningCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
