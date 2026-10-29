package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
// Its own context (no MockMvc), so no other test has changed the seeded admin before it is checked.
@SpringBootTest(properties = {"DEFAULT_ADMIN_EMAIL=" + ADMIN_EMAIL, "DEFAULT_ADMIN_PASSWORD=" + ADMIN_PASSWORD})
@ActiveProfiles("test")
class DataSeederDefaultAdminTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateDefaultAdminWhenEnvironmentVariablesAreProvided() {
        User admin = userRepository.findByEmailWithRoles(ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getDisplayName()).isEqualTo("Platform Administrator");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(admin.getRoles())
                .extracting(Role::getName)
                .contains(RoleName.ADMIN);
    }
}
