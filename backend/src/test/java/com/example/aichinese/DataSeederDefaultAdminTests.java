package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.UserRepository;

/**
 * Pins the {@code DataSeeder} contract when bootstrap-admin env vars
 * are configured: a default admin user is provisioned with the ADMIN
 * role and the supplied credentials, ready for first-run login.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@ActiveProfiles("test")
class DataSeederDefaultAdminTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateDefaultAdminWhenEnvironmentVariablesAreProvided() {
        User admin = userRepository.findByEmailWithRoles("admin@example.com").orElseThrow();

        assertThat(admin.getDisplayName()).isEqualTo("Platform Administrator");
        assertThat(admin.getPasswordHash()).isNotBlank();
        assertThat(admin.getRoles())
                .extracting(role -> role.getName())
                .contains(RoleName.ADMIN);
    }
}
