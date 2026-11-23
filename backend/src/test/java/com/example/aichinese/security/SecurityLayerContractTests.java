package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.UserRepository;

class SecurityLayerContractTests {

    private static final long USER_ID = 1L;
    private static final long PERSISTED_USER_ID = 42L;
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String SAME_EMAIL = "same@example.com";
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuv";
    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";
    private static final String ADMIN_EMAIL_WITH_IDEOGRAPHIC_SPACES =
            IDEOGRAPHIC_SPACE + ADMIN_EMAIL + IDEOGRAPHIC_SPACE;

    @Test
    void securityUserDetailsListsRolesByName() {
        User user = user(USER_ID, ADMIN_EMAIL);
        user.setRoles(Set.of(role(RoleName.STUDENT), role(RoleName.ADMIN)));

        SecurityUserDetails details = new SecurityUserDetails(user);

        assertThat(details.hasRole(RoleName.ADMIN)).isTrue();
        assertThat(details.hasRole(RoleName.STUDENT)).isTrue();
        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly(ADMIN_AUTHORITY, STUDENT_AUTHORITY);
    }

    @Test
    void transientSecurityUserDetailsDoNotCompareEqual() {
        SecurityUserDetails first = new SecurityUserDetails(user(null, SAME_EMAIL));
        SecurityUserDetails second = new SecurityUserDetails(user(null, SAME_EMAIL));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    @Test
    void customUserDetailsServiceNormalizesUsernameBeforeRepositoryLookup() {
        UserRepository repository = mock(UserRepository.class);
        User persistedUser = user(PERSISTED_USER_ID, ADMIN_EMAIL);
        when(repository.findByEmailWithRoles(ADMIN_EMAIL)).thenReturn(Optional.of(persistedUser));

        SecurityUserDetails details = (SecurityUserDetails) new CustomUserDetailsService(repository)
                .loadUserByUsername(ADMIN_EMAIL_WITH_IDEOGRAPHIC_SPACES);

        assertThat(details.getUserId()).isEqualTo(PERSISTED_USER_ID);
        verify(repository).findByEmailWithRoles(ADMIN_EMAIL);
    }

    @Test
    void customUserDetailsServiceRejectsBlankUsernameWithoutRepositoryLookup() {
        UserRepository repository = mock(UserRepository.class);

        assertThatThrownBy(() -> new CustomUserDetailsService(repository).loadUserByUsername(IDEOGRAPHIC_SPACE))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(repository);
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return user;
    }

    private static Role role(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        return role;
    }
}
