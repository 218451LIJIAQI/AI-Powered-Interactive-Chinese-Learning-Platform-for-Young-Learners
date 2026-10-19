package com.example.aichinese;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that boots the full Spring application context with the
 * {@code test} profile. A regression here usually means a misconfigured
 * bean wiring or an invalid test-profile property — caught before any
 * feature-level test runs. (Migrations are covered by
 * DatabaseMigrationConfigurationTests and MySqlMigrationSmokeTests.)
 */
@SpringBootTest
@ActiveProfiles("test")
class AichineseApplicationTests {

    @Test
    void contextLoads() {
    }

}
