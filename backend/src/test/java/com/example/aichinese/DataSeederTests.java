package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.RewardBadge;

/**
 * Pins the {@code DataSeeder} contract when the bootstrap-admin env vars are blank: roles and reference data are still
 * seeded, but no default admin account is created, so no privileged account exists without explicit configuration.
 */
@SpringBootTest(properties = {"DEFAULT_ADMIN_EMAIL=", "DEFAULT_ADMIN_PASSWORD=",
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=3000"})
@ActiveProfiles("test")
class DataSeederTests {

    @Autowired
    private RoleRepository roleRepository;

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
        // Learners and admins: there are no teachers.
        assertThat(roleRepository.findAll()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.STUDENT, RoleName.ADMIN);
        assertThat(learningCategoryRepository.findAllByOrderBySortOrderAscNameAsc())
                .extracting(LearningCategoryResponse::name)
                .containsExactly("Travel", "Work and career", "Study or exchange", "Daily life", "Personal interest");
        // SDD 5.5: the seeded catalogue is exactly the badges the reward service awards.
        assertThat(badgeRepository.findAll())
                .extracting(Badge::getName, Badge::getDescription)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(RewardBadge.values())
                        .map(reward -> tuple(reward.badgeName(), reward.description()))
                        .toList());
        assertThat(systemSettingRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(userRepository.count()).isZero();
    }
}
