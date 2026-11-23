package com.example.aichinese.entity;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.annotations.DynamicUpdate;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.RoleName;
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
import lombok.Setter;

@Getter
@Setter
@Entity
// A profile/preference edit must not write stale passwords, session versions or account status from its earlier read.
@DynamicUpdate
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

    /** Changed only through a confirmed e-mail change link (AccountEmailService). */
    @Column(name = "email", nullable = false, length = 150)
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

    /** Set when the owner deleted the account: the row stays (other records point to it) but is anonymised. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** When the owner opened a confirmation link sent to this address; null while a new account's address waits. */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** An admin's "Daily summary" e-mail (AI reports and support requests waiting); Account › Email notifications. */
    @Column(name = "notify_review_queues", nullable = false)
    private boolean notifyReviewQueues = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /** The sign-in response and permissions use the same order. A dual-role account opens the admin dashboard. */
    public Stream<RoleName> orderedRoleNames() {
        return roles.stream()
                .map(Role::getName)
                .sorted(Comparator.comparingInt((RoleName role) -> role == RoleName.ADMIN ? 0 : 1)
                        .thenComparing(RoleName::name));
    }

    public RoleName requirePrimaryRole() {
        return orderedRoleNames().findFirst()
                .orElseThrow(() -> new IllegalStateException("Account " + getId() + " has no role."));
    }
}
