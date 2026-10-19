package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.UserRepository;

/**
 * Pins the {@code DataSeeder} contract when bootstrap-admin env vars
 * are configured: a default admin user is provisioned with the ADMIN
 * role and the supplied credentials, ready for first-run login.
 */
@SpringBootTest(properties = {
        DataSeederDefaultAdminTests.DEFAULT_ADMIN_EMAIL_PROPERTY,
        DataSeederDefaultAdminTests.DEFAULT_ADMIN_PASSWORD_PROPERTY
})
@ActiveProfiles("test")
class DataSeederDefaultAdminTests {

    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.com";
    static final String DEFAULT_ADMIN_EMAIL_PROPERTY = "DEFAULT_ADMIN_EMAIL=" + DEFAULT_ADMIN_EMAIL;
    static final String DEFAULT_ADMIN_PASSWORD_PROPERTY = "DEFAULT_ADMIN_PASSWORD=SuperSecure123!";
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "Platform Administrator";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateDefaultAdminWhenEnvironmentVariablesAreProvided() {
        User admin = userRepository.findByEmailWithRoles(DEFAULT_ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getDisplayName()).isEqualTo(DEFAULT_ADMIN_DISPLAY_NAME);
        assertThat(passwordEncoder.matches("SuperSecure123!", admin.getPasswordHash())).isTrue();
        assertThat(admin.getRoles())
                .extracting(Role::getName)
                .contains(RoleName.ADMIN);
    }
}
