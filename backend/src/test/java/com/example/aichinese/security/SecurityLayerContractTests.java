package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

    private static final long USER_ID = 1L;
    private static final long PERSISTED_USER_ID = 42L;
    private static final String TEACHER_EMAIL = "teacher@example.com";
    private static final String SAME_EMAIL = "same@example.com";
    private static final String DISPLAY_NAME = "Security Test User";
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuv";
    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String ADMIN_ROLE_NAME = "ADMIN";
    private static final String TEACHER_ROLE_NAME = "TEACHER";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String TEACHER_AUTHORITY = "ROLE_TEACHER";
    private static final String TEACHER_EMAIL_WITH_IDEOGRAPHIC_SPACES =
            IDEOGRAPHIC_SPACE + TEACHER_EMAIL + IDEOGRAPHIC_SPACE;

    @Test
    void securityUserDetailsFiltersNullAndDuplicateRoles() {
        User user = user(USER_ID, TEACHER_EMAIL);
        user.setRoles(new HashSet<>(Set.of(
                role(RoleName.TEACHER),
                role(RoleName.ADMIN),
                role(RoleName.TEACHER),
                new Role())));
        user.getRoles().add(null);

        SecurityUserDetails details = new SecurityUserDetails(user);

        assertThat(details.getRoleNames()).containsExactly(ADMIN_ROLE_NAME, TEACHER_ROLE_NAME);
        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly(ADMIN_AUTHORITY, TEACHER_AUTHORITY);
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
        User persistedUser = user(PERSISTED_USER_ID, TEACHER_EMAIL);
        when(repository.findByEmailWithRoles(TEACHER_EMAIL)).thenReturn(Optional.of(persistedUser));

        SecurityUserDetails details = (SecurityUserDetails) new CustomUserDetailsService(repository)
                .loadUserByUsername(TEACHER_EMAIL_WITH_IDEOGRAPHIC_SPACES);

        assertThat(details.getUserId()).isEqualTo(PERSISTED_USER_ID);
        verify(repository).findByEmailWithRoles(TEACHER_EMAIL);
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
        user.setDisplayName(DISPLAY_NAME);
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
