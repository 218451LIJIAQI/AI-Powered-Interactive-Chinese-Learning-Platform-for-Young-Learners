package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authorisation role reference table seeded with one row per
 * {@link com.example.aichinese.entity.enums.RoleName} constant.
 *
 * <p>The {@link User#getRoles()} {@code ManyToMany} association joins
 * through {@code user_roles}; in production every user references a
 * single role from this table. Keeping the role as its own table (as
 * opposed to denormalising the enum onto {@code users}) lets future
 * iterations attach per-role metadata (description, permissions list,
 * UI styling) without a schema change.</p>
 *
 * <p>Uniqueness of {@link #name} is enforced by a single named
 * constraint ({@code idx_roles_name}) declared via {@link UniqueConstraint}
 * on the {@link Table @Table} annotation, matching the Flyway baseline.
 * The historical {@code idx_} prefix is preserved to match the
 * database. {@link #name} is also mapped {@code updatable = false}: a
 * row is the database mirror of a fixed {@link RoleName} enum constant
 * and is never re-targeted to a different role; only {@link #description}
 * stays mutable to support future per-role descriptive metadata.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_roles_name", columnNames = "name")
        }
)
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, updatable = false, length = 50)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;
}
