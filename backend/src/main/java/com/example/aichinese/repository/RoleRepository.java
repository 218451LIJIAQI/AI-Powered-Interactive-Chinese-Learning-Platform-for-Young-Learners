package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.enums.RoleName;

/**
 * Persistence operations for {@link Role} \u2014 the platform's role
 * dictionary (STUDENT, TEACHER, ADMIN) referenced through the
 * many-to-many {@code user_roles} join table.
 *
 * <p>The table is seeded once by {@code DataSeeder} and treated as
 * read-only at runtime; every authentication path that needs a role
 * reference looks it up by its {@link RoleName} enum (rather than by
 * id) so the resolution survives id reshuffles across environments.</p>
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Looks up a role by its typed enum name. Backed by the unique
     * index on the {@code name} column so the lookup stays
     * O(log&nbsp;n). Used by {@code DataSeeder} for idempotent seeding
     * and by the role-assignment service when promoting a user.
     */
    Optional<Role> findByName(RoleName name);
}
