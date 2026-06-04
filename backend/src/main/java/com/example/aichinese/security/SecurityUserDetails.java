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
            List<RoleName> validRoleNames = roles.stream()
                    .filter(Objects::nonNull)
                    .map(Role::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparing(RoleName::name))
                    .toList();
            this.authorities = validRoleNames.stream()
                    .map(roleName -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + roleName.name()))
                    .toList();
            this.roleNames = validRoleNames.stream()
                    .map(RoleName::name)
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
        Long id = user.getId();
        return id != null && id.equals(that.user.getId());
    }

    @Override
    public int hashCode() {
        Long id = user.getId();
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    @Override
    public String toString() {
        return "SecurityUserDetails[id=" + user.getId() + ", active=" + user.isActive() + "]";
    }
}
