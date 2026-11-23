package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.config.seed.DataSeeder;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.ConcurrentTransactions;

/**
 * Pins the {@code DataSeeder} contract when the bootstrap-admin env vars are configured: a default admin user is
 * provisioned with the ADMIN role and the supplied credentials, ready for first-run login.
 */
// Its own context (no MockMvc), so no other test has changed the seeded admin before it is checked.
@SpringBootTest(properties = {"DEFAULT_ADMIN_EMAIL=" + ADMIN_EMAIL, "DEFAULT_ADMIN_PASSWORD=" + ADMIN_PASSWORD})
@ActiveProfiles("test")
class DataSeederDefaultAdminTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private SystemSettingRepository settings;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldCreateDefaultAdminWhenEnvironmentVariablesAreProvided() {
        User admin = userRepository.findByEmailWithRoles(ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getDisplayName()).isEqualTo("Platform Administrator");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(admin.getRoles())
                .extracting(Role::getName)
                .contains(RoleName.ADMIN);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aConcurrentStartupSeesOnlyCommittedSeedData(boolean commit) throws Exception {
        String marker = "test_concurrent_seed_" + commit;
        if (commit) {
            // Exercise the very first boot, when neither instance can lock an existing row.
            jdbc.update("delete from system_settings where setting_key = 'startup_seed_lock'");
        }
        Boolean visible = ConcurrentTransactions.withRowLock(jdbc, "system_settings",
                pause -> transactions.executeWithoutResult(status -> {
                    settings.lockForSeeding();
                    SystemSetting setting = new SystemSetting();
                    setting.setSettingKey(marker);
                    setting.setSettingValue("seeded");
                    settings.saveAndFlush(setting);
                    pause.run();
                    if (!commit) {
                        status.setRollbackOnly();
                    }
                }),
                () -> transactions.execute(status -> {
                    seeder.run();
                    return settings.existsBySettingKey(marker);
                }));

        assertThat(visible).isEqualTo(commit);
    }
}
