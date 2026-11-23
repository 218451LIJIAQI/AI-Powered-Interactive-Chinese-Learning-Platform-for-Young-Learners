package com.example.aichinese.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.service.account.AccountGuards;

public final class SecurityUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean active;
    private final long sessionVersion;
    private final List<GrantedAuthority> authorities;
    private final boolean persistentSession;
    /** The browser session (sign-in) of the request's access token; null when the token names none. */
    private final String sessionId;

    public SecurityUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        this.active = AccountGuards.isAvailable(user);
        this.sessionVersion = user.getSessionVersion();
        this.authorities = user.orderedRoleNames()
                .<GrantedAuthority>map(roleName -> new SimpleGrantedAuthority(roleName.authority()))
                .toList();
        this.persistentSession = true;
        this.sessionId = null;
    }

    private SecurityUserDetails(SecurityUserDetails source, boolean persistentSession, String sessionId) {
        this.userId = source.userId;
        this.username = source.username;
        // JWT-authenticated requests never use password credentials; keep hashes out of SecurityContextHolder.
        this.password = null;
        this.active = source.active;
        this.sessionVersion = source.sessionVersion;
        this.authorities = source.authorities;
        this.persistentSession = persistentSession;
        this.sessionId = sessionId;
    }

    /**
     * The same account, signed in by a request whose access token carries this "Keep me signed in" choice and names this
     * browser session.
     */
    public SecurityUserDetails withSession(boolean persistent, String sessionId) {
        return new SecurityUserDetails(this, persistent, sessionId);
    }

    public Long getUserId() {
        return userId;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    /** Whether the sign-in behind this request chose "Keep me signed in" (true when the token does not say). */
    public boolean isPersistentSession() {
        return persistentSession;
    }

    /** The browser session of the request (Account › This device); null when the token names none. */
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public boolean hasRole(RoleName role) {
        String authority = role.authority();
        return authorities.stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SecurityUserDetails that)) {
            return false;
        }
        return userId != null && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }

    @Override
    public String toString() {
        return "SecurityUserDetails[id=" + userId + ", active=" + active + "]";
    }
}
