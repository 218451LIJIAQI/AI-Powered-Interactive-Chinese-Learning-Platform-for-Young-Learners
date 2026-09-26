package com.example.aichinese.config.seed;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;

@Component
@Order(0)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final Map<RoleName, String> ROLE_DESCRIPTIONS = Map.of(
            RoleName.STUDENT, "Student role for learners using the platform.",
            RoleName.TEACHER, "Teacher role for educators creating and managing learning content.",
            RoleName.ADMIN, "Admin role for platform management and moderation.");
    private static final List<String> LEARNING_LEVELS = List.of("Beginner", "Intermediate", "Advanced");
    private static final List<String> LEARNING_CATEGORIES = List.of(
            "Pinyin",
            "Tones",
            "Vocabulary",
            "Chinese Characters",
            "Listening",
            "Reading",
            "Writing / Tracing",
            "Daily Conversation",
            "Numbers",
            "Colours",
            "Animals",
            "Family",
            "School",
            "Food",
            "Greetings");
    private static final List<BadgeSeed> BADGES = List.of(
            new BadgeSeed("First Steps", "Awarded for starting the learning journey.", "badge-first-steps"),
            new BadgeSeed("Quiz Explorer", "Awarded for completing early quiz practice.", "badge-quiz-explorer"),
            new BadgeSeed("Daily Spark", "Awarded for building a learning streak.", "badge-daily-spark"));
    private static final List<SettingSeed> SYSTEM_SETTINGS = List.of(
            new SettingSeed(
                    "platform_name",
                    "AI-Powered Interactive Chinese Learning Platform for Young Learners",
                    "Displayed platform name.",
                    false),
            new SettingSeed(
                    "default_currency",
                    "MYR",
                    "Default currency for the Malaysian deployment context.",
                    true),
            new SettingSeed(
                    "platform_commission_rate",
                    "0.20",
                    "Editable platform commission rate represented as a decimal.",
                    true),
            new SettingSeed(
                    "ai_disclaimer_text",
                    "AI-generated content may not always be accurate. Please review before use.",
                    "Shown on AI-powered learning experiences.",
                    true),
            new SettingSeed(
                    "admin_email_notifications_enabled",
                    "true",
                    "Enable email notifications for admin workflows.",
                    true),
            new SettingSeed(
                    "admin_in_app_notifications_enabled",
                    "true",
                    "Enable in-app notifications for admin workflows.",
                    true),
            new SettingSeed(
                    "admin_daily_summary_enabled",
                    "false",
                    "Enable daily admin summary delivery.",
                    true),
            new SettingSeed(
                    "strict_content_filtering_enabled",
                    "true",
                    "Enable strict content filtering for safety-sensitive flows.",
                    true),
            new SettingSeed(
                    "sensitive_word_filter_enabled",
                    "true",
                    "Enable profanity and sensitive word filtering.",
                    true),
            new SettingSeed(
                    "ai_safety_check_enabled",
                    "true",
                    "Enable AI safety checks before AI guidance is shown.",
                    true));

    private final RoleRepository roleRepository;
    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;
    private final BadgeRepository badgeRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEFAULT_ADMIN_EMAIL:}")
    private String defaultAdminEmail;

    @Value("${DEFAULT_ADMIN_PASSWORD:}")
    private String defaultAdminPassword;

    public DataSeeder(
            RoleRepository roleRepository,
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository,
            BadgeRepository badgeRepository,
            SystemSettingRepository systemSettingRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.learningLevelRepository = Objects.requireNonNull(learningLevelRepository, "learningLevelRepository");
        this.learningCategoryRepository = Objects.requireNonNull(learningCategoryRepository, "learningCategoryRepository");
        this.badgeRepository = Objects.requireNonNull(badgeRepository, "badgeRepository");
        this.systemSettingRepository = Objects.requireNonNull(systemSettingRepository, "systemSettingRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedLearningLevels();
        seedLearningCategories();
        seedBadges();
        seedSystemSettings();
        seedDefaultAdmin();
    }

    private void seedRoles() {
        ROLE_DESCRIPTIONS.forEach((name, description) -> roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            return roleRepository.save(role);
        }));
    }

    private void seedLearningLevels() {
        for (int i = 0; i < LEARNING_LEVELS.size(); i++) {
            String name = LEARNING_LEVELS.get(i);
            int sortOrder = i + 1;
            learningLevelRepository.findByName(name).orElseGet(() -> {
                LearningLevel level = new LearningLevel();
                level.setName(name);
                level.setDescription(name + " level learning path.");
                level.setSortOrder(sortOrder);
                return learningLevelRepository.save(level);
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
                category.setDescription(name + " learning category.");
                category.setSortOrder(sortOrder);
                return learningCategoryRepository.save(category);
            });
        }
    }

    private void seedBadges() {
        for (BadgeSeed seed : BADGES) {
            badgeRepository.findByName(seed.name()).orElseGet(() -> {
                Badge badge = new Badge();
                badge.setName(seed.name());
                badge.setDescription(seed.description());
                badge.setIconKey(seed.iconKey());
                return badgeRepository.save(badge);
            });
        }
    }

    private void seedSystemSettings() {
        SYSTEM_SETTINGS.forEach(seed -> systemSettingRepository.findBySettingKey(seed.key()).orElseGet(() -> {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(seed.key());
            setting.setSettingValue(seed.value());
            setting.setDescription(seed.description());
            setting.setEditable(seed.editable());
            return systemSettingRepository.save(setting);
        }));
    }

    private record BadgeSeed(String name, String description, String iconKey) {
    }

    private record SettingSeed(String key, String value, String description, boolean editable) {
    }

    private void seedDefaultAdmin() {
        if (isBlank(defaultAdminEmail) || isBlank(defaultAdminPassword)) {
            log.warn("Default admin credentials are not configured. Skipping default admin creation safely.");
            return;
        }

        String normalizedEmail = defaultAdminEmail.strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role must exist before admin seeding."));

        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
        admin.setDisplayName("Platform Administrator");
        admin.setPreferredLanguage("en");
        admin.setActive(true);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        log.info("Default admin account created.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
