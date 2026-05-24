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
 * Achievement badge that students can earn (e.g. "First Quiz",
 * "Seven-Day Streak", "Pinyin Master").
 *
 * <p>Awarding is recorded in {@link StudentBadge}; this entity is just
 * the catalogue of badge definitions. {@link #iconKey} is a stable
 * short string (e.g. {@code "streak-7"}) that the frontend maps to a
 * concrete icon asset, decoupling badge metadata from front-end visual
 * design.</p>
 *
 * <p>{@link #active} provides soft-deactivation: a retired badge is
 * hidden from new awards but historic {@link StudentBadge} rows
 * continue to reference it without breaking.</p>
 *
 * <p>{@link #name} is unique to keep the gamification dashboard free
 * of duplicate displayed achievements.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "badges", indexes = {
        @Index(name = "idx_badges_name", columnList = "name", unique = true)
})
public class Badge extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "icon_key", length = 100)
    private String iconKey;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
