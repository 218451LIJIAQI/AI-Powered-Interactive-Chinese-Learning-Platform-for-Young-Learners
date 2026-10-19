package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;

/**
 * Pins the {@code DataSeeder} contract when the bootstrap-admin
 * env vars are blank: roles + reference data are still seeded, but
 * no default admin account is created. Prevents a future refactor
 * from silently provisioning a privileged account without explicit
 * configuration.
 */
@SpringBootTest(properties = {
        DataSeederTests.BLANK_DEFAULT_ADMIN_EMAIL_PROPERTY,
        DataSeederTests.BLANK_DEFAULT_ADMIN_PASSWORD_PROPERTY
})
@ActiveProfiles("test")
class DataSeederTests {

    static final String BLANK_DEFAULT_ADMIN_EMAIL_PROPERTY = "DEFAULT_ADMIN_EMAIL=";
    static final String BLANK_DEFAULT_ADMIN_PASSWORD_PROPERTY = "DEFAULT_ADMIN_PASSWORD=";
    private static final long EXPECTED_ROLE_COUNT = 3;
    private static final long EXPECTED_LEVEL_COUNT = 3;
    private static final long EXPECTED_CATEGORY_COUNT = 15;
    private static final long MIN_BADGE_COUNT = 3;
    private static final long MIN_SYSTEM_SETTING_COUNT = 3;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LearningLevelRepository learningLevelRepository;

    @Autowired
    private LearningCategoryRepository learningCategoryRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSeedEssentialReferenceDataWithoutDefaultAdmin() {
        assertThat(roleRepository.count()).isEqualTo(EXPECTED_ROLE_COUNT);
        assertThat(learningLevelRepository.count()).isEqualTo(EXPECTED_LEVEL_COUNT);
        assertThat(learningCategoryRepository.count()).isEqualTo(EXPECTED_CATEGORY_COUNT);
        assertThat(badgeRepository.count()).isGreaterThanOrEqualTo(MIN_BADGE_COUNT);
        // Seeded badges are exactly the badges the reward service awards (no never-awarded badges).
        assertThat(badgeRepository.findAll())
                .extracting(com.example.aichinese.entity.Badge::getName)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(com.example.aichinese.service.RewardBadge.values())
                        .map(com.example.aichinese.service.RewardBadge::badgeName)
                        .toArray(String[]::new));
        assertThat(badgeRepository.findAll())
                .allSatisfy(badge -> assertThat(badge.getDescription()).startsWith("Earned "));
        assertThat(systemSettingRepository.count()).isGreaterThanOrEqualTo(MIN_SYSTEM_SETTING_COUNT);
        assertThat(userRepository.count()).isZero();
    }
}
