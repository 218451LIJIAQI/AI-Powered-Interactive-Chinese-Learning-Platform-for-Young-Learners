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
 * Curated topical category (e.g. "Pinyin", "HSK 1 Vocabulary",
 * "Chinese Festivals") used to facet the public catalogue.
 *
 * <p>Categories are purely metadata; they do <em>not</em> grant access
 * or define a content hierarchy. The student-facing catalogue groups
 * lessons, modules, and packages by their assigned category and orders
 * the categories themselves by {@link #sortOrder} (ascending).</p>
 *
 * <p>{@link #name} is unique to keep the public catalogue free of
 * confusing duplicates.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_categories", indexes = {
        @Index(name = "idx_learning_categories_name", columnList = "name", unique = true)
})
public class LearningCategory extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
