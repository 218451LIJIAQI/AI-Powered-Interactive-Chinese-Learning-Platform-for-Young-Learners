package com.example.aichinese.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;

/**
 * Spring Security {@link UserDetails} adapter wrapping the platform's
 * {@link User} entity. Built by {@link CustomUserDetailsService} on every
 * authenticated request so the rest of the security chain has a
 * Spring-native view of the principal.
 *
 * <h2>Account-status flags</h2>
 * <ul>
 *   <li>{@link #isEnabled()} delegates to {@link User#isActive()} so an
 *       admin can immediately disable a misbehaving account by flipping
 *       a single column — every subsequent request fails authentication
 *       with the standard "session expired" envelope.</li>
 *   <li>{@link #isAccountNonLocked()} / {@link #isAccountNonExpired()} /
 *       {@link #isCredentialsNonExpired()} all return {@code true}
 *       unconditionally. Login lockout and rate limiting are handled
 *       <em>before</em> the {@link UserDetailsService} is consulted (by
 *       {@link LoginAttemptLimiter} / {@link PublicAuthRequestLimiter}),
 *       so duplicating the check here would be redundant and harder to
 *       reason about. Forced password expiry is not a platform feature.</li>
 * </ul>
 *
 * <h2>Authority mapping</h2>
 * <p>Each {@code Role} on the user is mapped to a Spring authority
 * prefixed with {@code "ROLE_"} so that
 * {@code @PreAuthorize("hasRole('ADMIN')")} matches {@code ROLE_ADMIN}
 * without manual configuration. {@link #getRoleNames()} exposes the
 * un-prefixed list for response DTOs that want to render role names
 * to humans.</p>
 *
 * <h2>Username = email</h2>
 * <p>{@link #getUsername()} returns {@link User#getEmail()} because the
 * platform identifies users by email, not by a separate username field.
 * The case-insensitive uniqueness contract on the email column is
 * preserved everywhere this principal is consumed.</p>
 */
public final class SecurityUserDetails implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    private final User user;
    private final List<GrantedAuthority> authorities;
    private final List<String> roleNames;

    public SecurityUserDetails(User user) {
        this.user = Objects.requireNonNull(user, "user");
        Set<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            this.authorities = List.of();
            this.roleNames = List.of();
        } else {
            this.authorities = roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role.getName().name()))
                    .toList();
            this.roleNames = roles.stream()
                    .map(role -> role.getName().name())
                    .sorted()
                    .toList();
        }
    }

    public Long getUserId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SecurityUserDetails that)) {
            return false;
        }
        return Objects.equals(user.getId(), that.user.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user.getId());
    }

    /**
     * Diagnostic-only representation. Deliberately omits the password hash and
     * email body so a stray {@code log.info(principal)} cannot leak credentials
     * or PII into log aggregators.
     */
    @Override
    public String toString() {
        return "SecurityUserDetails[id=" + user.getId() + ", active=" + user.isActive() + "]";
    }
}
