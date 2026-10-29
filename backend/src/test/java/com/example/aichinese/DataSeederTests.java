package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.entity.Badge;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.RewardBadge;

/**
 * Pins the {@code DataSeeder} contract when the bootstrap-admin
 * env vars are blank: roles + reference data are still seeded, but
 * no default admin account is created. Prevents a future refactor
 * from silently provisioning a privileged account without explicit
 * configuration.
 */
@SpringBootTest(properties = {"DEFAULT_ADMIN_EMAIL=", "DEFAULT_ADMIN_PASSWORD="})
@ActiveProfiles("test")
class DataSeederTests {

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
        assertThat(roleRepository.count()).isEqualTo(3);
        assertThat(learningLevelRepository.count()).isEqualTo(2);
        assertThat(learningCategoryRepository.count()).isEqualTo(9);
        // SDD 5.5: the seeded catalogue is exactly the badges the reward service awards.
        assertThat(badgeRepository.findAll())
                .extracting(Badge::getName)
                .containsExactlyInAnyOrder(Arrays.stream(RewardBadge.values())
                        .map(RewardBadge::badgeName)
                        .toArray(String[]::new));
        assertThat(badgeRepository.findAll())
                .allSatisfy(badge -> assertThat(badge.getDescription())
                        .isEqualTo(Arrays.stream(RewardBadge.values())
                                .filter(reward -> reward.badgeName().equals(badge.getName()))
                                .findFirst().orElseThrow().description()));
        assertThat(systemSettingRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(userRepository.count()).isZero();
    }
}
