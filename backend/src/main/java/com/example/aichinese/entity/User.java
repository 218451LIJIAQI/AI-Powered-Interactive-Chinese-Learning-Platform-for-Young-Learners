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
 *
 * <p>Uniqueness of {@link #email} is enforced by a single named
 * constraint ({@code idx_users_email}) declared via
 * {@link UniqueConstraint} on the {@link Table @Table} annotation,
 * matching the Flyway baseline. {@link #email} is also mapped
 * {@code updatable = false}: {@code AuthService} sets it once at
 * registration and the codebase has no email-change flow. If a real
 * email-change feature is later added, removing this attribute will
 * be a deliberate, reviewable diff that forces the implementer to
 * also address the related re-verification and identity-merge
 * concerns rather than silently mutate the login identity.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_users_email", columnNames = "email")
        }
)
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, updatable = false, length = 150)
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
