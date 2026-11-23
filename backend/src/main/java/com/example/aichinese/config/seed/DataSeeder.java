package com.example.aichinese.config.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.ProductionProfiles;

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
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.PlatformSettingsService.SettingDefinition;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.learn.HintPolicyService;
import com.example.aichinese.util.LanguageCodes;

import lombok.RequiredArgsConstructor;

/**
 * Reference data every installation needs, in every profile: the roles, the class themes, the badges, the platform
 * settings' defaults and, when configured, the default admin. Rows are only added when missing (a badge row is kept
 * aligned with {@link RewardBadge}), so the seeder runs safely on every start against a persistent database.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    /** Class themes (the "topics" of the class catalogue API). */
    private static final List<String> LEARNING_CATEGORIES = List.of(
            "Travel",
            "Work and career",
            "Study or exchange",
            "Daily life",
            "Personal interest");

    private static final int MIN_ADMIN_PASSWORD_LENGTH = 8;

    private final RoleRepository roleRepository;
    private final LearningCategoryRepository learningCategoryRepository;
    private final BadgeRepository badgeRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final Environment environment;

    @Value("${app.bootstrap-admin.email:}")
    private String defaultAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String defaultAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        systemSettingRepository.lockForSeeding();
        seedRoles();
        seedLearningCategories();
        seedBadges();
        seedSystemSettings();
        seedDefaultAdmin();
    }

    private void seedRoles() {
        for (RoleName name : RoleName.values()) {
            roleRepository.findByName(name).orElseGet(() -> {
                Role role = new Role();
                role.setName(name);
                return roleRepository.save(role);
            });
        }
    }

    private void seedLearningCategories() {
        for (int i = 0; i < LEARNING_CATEGORIES.size(); i++) {
            String name = LEARNING_CATEGORIES.get(i);
            int sortOrder = i + 1;
            learningCategoryRepository.findByName(name).orElseGet(() -> {
                LearningCategory category = new LearningCategory();
                category.setName(name);
                category.setSortOrder(sortOrder);
                return learningCategoryRepository.save(category);
            });
        }
    }

    /** Exactly the badges the reward service awards; a stored row is kept aligned with RewardBadge (the earning rule). */
    private void seedBadges() {
        for (RewardBadge seed : RewardBadge.values()) {
            Badge badge = badgeRepository.findByName(seed.badgeName()).orElseGet(Badge::new);
            badge.setName(seed.badgeName());
            badge.setDescription(seed.description());
            badge.setIconKey(seed.iconKey());
            badge.setActive(true);
            badgeRepository.save(badge);
        }
    }

    private void seedSystemSettings() {
        for (SettingDefinition definition : PlatformSettingsService.SETTING_DEFINITIONS) {
            if (!systemSettingRepository.existsBySettingKey(definition.key())) {
                systemSettingRepository.save(definition.newSetting());
            }
        }
        if (!systemSettingRepository.existsBySettingKey(HintPolicyService.POLICY_KEY)) {
            systemSettingRepository.save(HintPolicyService.defaultSetting());
        }
    }

    private void seedDefaultAdmin() {
        if (defaultAdminEmail.isBlank() || defaultAdminPassword.isBlank()) {
            log.warn("Default admin credentials are not configured. Skipping default admin creation safely.");
            return;
        }
        if (ProductionProfiles.isActive(environment)
                && (ProductionProfiles.isPlaceholder(defaultAdminPassword)
                        || defaultAdminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH)) {
            throw new IllegalStateException(
                    "DEFAULT_ADMIN_PASSWORD must be at least 8 characters and not a placeholder value when the prod profile is active.");
        }

        String normalizedEmail = AuthService.normalizeEmail(defaultAdminEmail);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return;
        }

        authService.createAccount(
                "Platform Administrator", normalizedEmail, defaultAdminPassword, LanguageCodes.ENGLISH, RoleName.ADMIN);
        log.info("Default admin account created.");
    }
}
