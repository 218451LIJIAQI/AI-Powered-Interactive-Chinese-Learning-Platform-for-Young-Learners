package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_roles_name", columnList = "name", unique = true)
})
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;
}
