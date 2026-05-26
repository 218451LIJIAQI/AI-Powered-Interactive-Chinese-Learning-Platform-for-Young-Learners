package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.UserRepository;

class SecurityLayerContractTests {

    @Test
    void securityUserDetailsFiltersNullAndDuplicateRoles() {
        User user = user(1L, "teacher@example.com");
        user.setRoles(new HashSet<>(Set.of(
                role(RoleName.TEACHER),
                role(RoleName.ADMIN),
                role(RoleName.TEACHER),
                new Role())));
        user.getRoles().add(null);

        SecurityUserDetails details = new SecurityUserDetails(user);

        assertThat(details.getRoleNames()).containsExactly("ADMIN", "TEACHER");
        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN", "ROLE_TEACHER");
    }

    @Test
    void transientSecurityUserDetailsDoNotCompareEqual() {
        SecurityUserDetails first = new SecurityUserDetails(user(null, "same@example.com"));
        SecurityUserDetails second = new SecurityUserDetails(user(null, "same@example.com"));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    @Test
    void customUserDetailsServiceNormalizesUsernameBeforeRepositoryLookup() {
        UserRepository repository = org.mockito.Mockito.mock(UserRepository.class);
        User persistedUser = user(42L, "teacher@example.com");
        when(repository.findByEmailWithRoles("teacher@example.com")).thenReturn(Optional.of(persistedUser));

        CustomUserDetailsService service = new CustomUserDetailsService(repository);
        SecurityUserDetails details = (SecurityUserDetails) service.loadUserByUsername("\u3000teacher@example.com\u3000");

        assertThat(details.getUserId()).isEqualTo(42L);
        verify(repository).findByEmailWithRoles("teacher@example.com");
    }

    @Test
    void customUserDetailsServiceRejectsBlankUsernameWithoutRepositoryLookup() {
        UserRepository repository = org.mockito.Mockito.mock(UserRepository.class);
        CustomUserDetailsService service = new CustomUserDetailsService(repository);

        assertThatThrownBy(() -> service.loadUserByUsername("\u3000"))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(repository);
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName("Security Test User");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setActive(true);
        return user;
    }

    private static Role role(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        return role;
    }
}
