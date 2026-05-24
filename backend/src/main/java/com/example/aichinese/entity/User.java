package com.example.aichinese.entity;

import java.util.HashSet;
import java.util.Set;

import com.example.aichinese.entity.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authentication root identifying a single human (student, teacher, or
 * administrator) in the platform.
 *
 * <p>Each user has exactly one entry in {@link #roles} (currently
 * {@link com.example.aichinese.entity.enums.RoleName#STUDENT},
 * {@code TEACHER}, or {@code ADMIN}). Role-specific profile data lives
 * in the companion {@link StudentProfile} or {@link TeacherProfile}
 * tables linked back via {@code user_id}.</p>
 *
 * <p>{@link #passwordHash} stores a BCrypt hash; the raw password is
 * never persisted. Jackson's {@link com.fasterxml.jackson.annotation.JsonIgnore}
 * on the field prevents accidental serialisation through any DTO that
 * embeds a {@code User} reference.</p>
 *
 * <p>{@link #sessionVersion} backs the JWT invalidation strategy: the
 * counter is bumped whenever a user changes password, is deactivated,
 * or has all sessions force-revoked by an admin. {@code JwtService}
 * encodes this number in the token; if the persisted value is greater,
 * the token is rejected at the authentication filter.</p>
 *
 * <p>{@link #active} flips to {@code false} when an admin deactivates
 * the account; the user record is preserved for audit-log integrity
 * rather than physically deleted.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_created_at", columnList = "created_at")
})
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion = 0L;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_roles_user_role", columnNames = { "user_id", "role_id" }))
    private Set<Role> roles = new HashSet<>();
}
