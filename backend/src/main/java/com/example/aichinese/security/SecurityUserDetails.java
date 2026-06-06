package com.example.aichinese.security;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

public final class SecurityUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean active;
    private final long sessionVersion;
    private final int fallbackHashCode;
    private final List<GrantedAuthority> authorities;
    private final List<String> roleNames;

    public SecurityUserDetails(User user) {
        Objects.requireNonNull(user, "user");
        this.userId = user.getId();
        this.username = user.getEmail();
        this.password = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.active = user.isActive();
        this.sessionVersion = user.getSessionVersion();
        this.fallbackHashCode = System.identityHashCode(user);
        Set<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            this.authorities = List.of();
            this.roleNames = List.of();
        } else {
            List<RoleName> validRoleNames = roles.stream()
                    .filter(Objects::nonNull)
                    .map(Role::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparing(RoleName::name))
                    .toList();
            this.authorities = validRoleNames.stream()
                    .map(roleName -> (GrantedAuthority) new SimpleGrantedAuthority(roleName.authority()))
                    .toList();
            this.roleNames = validRoleNames.stream()
                    .map(RoleName::name)
                    .toList();
        }
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSessionVersion() {
        return sessionVersion;
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
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return userId == null ? fallbackHashCode : userId.hashCode();
    }

    @Override
    public String toString() {
        return "SecurityUserDetails[id=" + userId + ", active=" + active + "]";
    }
}
