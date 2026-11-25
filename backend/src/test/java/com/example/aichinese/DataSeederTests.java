package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.aichinese.config.seed.DataSeeder;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.AuthService;
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

    @Test
    void rejectsMalformedOrUnsafeBootstrapAdminCredentialsBeforeCreatingAccount() {
        assertSeederFailure("not-an-email", "StrongBootstrap123!", false, "DEFAULT_ADMIN_EMAIL");
        assertSeederFailure("a".repeat(140) + "@example.com", "StrongBootstrap123!", false, "DEFAULT_ADMIN_EMAIL");
        assertSeederFailure("admin@example.com", "汉".repeat(25), false, "72 bytes");
        assertSeederFailure("admin@example.com", "Admin123!", true, "prod profile");
        assertSeederFailure("admin@example.com", "Short1!", true, "prod profile");
        assertSeederFailure("admin@example.com", "replace-with-strong-password", true, "prod profile");
    }

    private static void assertSeederFailure(String email, String password, boolean prod, String expectedMessage) {
        RoleRepository roles = mock(RoleRepository.class);
        LearningCategoryRepository categories = mock(LearningCategoryRepository.class);
        BadgeRepository badges = mock(BadgeRepository.class);
        SystemSettingRepository settings = mock(SystemSettingRepository.class);
        when(roles.findByName(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(new Role()));
        when(categories.findByName(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(new LearningCategory()));
        when(badges.findByName(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(new Badge()));
        when(settings.existsBySettingKey(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        MockEnvironment environment = new MockEnvironment();
        if (prod) {
            environment.setActiveProfiles("prod");
        }
        DataSeeder seeder = new DataSeeder(
                roles, categories, badges, settings, mock(UserRepository.class), mock(AuthService.class), environment);
        ReflectionTestUtils.setField(seeder, "defaultAdminEmail", email);
        ReflectionTestUtils.setField(seeder, "defaultAdminPassword", password);

        assertThatThrownBy(seeder::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }
}
