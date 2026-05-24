package com.example.aichinese.config.seed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Idempotent reference-data and bootstrap-admin seeder run on every
 * application startup.
 *
 * <p>The seeder is implemented as a {@link CommandLineRunner} (not a
 * Flyway migration) for two reasons:</p>
 * <ol>
 *   <li>The values reference enum types and platform-specific
 *       constants that would be brittle to express in raw SQL.</li>
 *   <li>Each entry uses a {@code findByName(...).orElseGet(create...)}
 *       upsert pattern, so re-running the seeder is a no-op rather
 *       than an error — perfect for the per-startup behaviour Spring
 *       Boot's {@code CommandLineRunner} provides.</li>
 * </ol>
 *
 * <h2>What is seeded</h2>
 * <ul>
 *   <li>{@link com.example.aichinese.entity.Role} — STUDENT / TEACHER /
 *       ADMIN with their human-readable descriptions.</li>
 *   <li>{@link com.example.aichinese.entity.LearningLevel} —
 *       Beginner / Intermediate / Advanced.</li>
 *   <li>{@link com.example.aichinese.entity.LearningCategory} — the
 *       fifteen Chinese-learning categories used by the discovery
 *       catalogue (Pinyin / Tones / Vocabulary / etc.).</li>
 *   <li>{@link com.example.aichinese.entity.Badge} — three starter
 *       achievement tokens plus their icon keys.</li>
 *   <li>{@link com.example.aichinese.entity.SystemSetting} — every
 *       runtime-tunable platform setting (currency, commission rate,
 *       AI safety toggles, etc.). Settings flagged as
 *       {@code editable=true} can later be tweaked from the admin
 *       settings page; non-editable rows are platform constants.</li>
 *   <li>An optional bootstrap admin user, created only when both
 *       {@code DEFAULT_ADMIN_EMAIL} and {@code DEFAULT_ADMIN_PASSWORD}
 *       environment variables are configured. Skipping silently when
 *       either is unset means a fresh deploy can be initialised by an
 *       operator who supplies these via secrets, without forcing
 *       every dev environment to have an admin account.</li>
 * </ul>
 *
 * <p>The whole {@link #run(String...)} method is wrapped in
 * {@link org.springframework.transaction.annotation.Transactional @Transactional}
 * so the entire seed runs atomically — either every reference row is
 * present or none of them are, preventing half-seeded state if the
 * application crashes mid-startup.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

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
        this.roleRepository = roleRepository;
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
        this.badgeRepository = badgeRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        Map<RoleName, String> roles = Map.of(
                RoleName.STUDENT, "Student role for learners using the platform.",
                RoleName.TEACHER, "Teacher role for educators creating and managing learning content.",
                RoleName.ADMIN, "Admin role for platform management and moderation.");

        roles.forEach((name, description) -> roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            return roleRepository.save(role);
        }));
    }

    private void seedLearningLevels() {
        List<String> levels = List.of("Beginner", "Intermediate", "Advanced");
        for (int i = 0; i < levels.size(); i++) {
            String name = levels.get(i);
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
        List<String> categories = List.of(
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

        for (int i = 0; i < categories.size(); i++) {
            String name = categories.get(i);
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
        String[][] badges = {
                {"First Steps", "Awarded for starting the learning journey.", "badge-first-steps"},
                {"Quiz Explorer", "Awarded for completing early quiz practice.", "badge-quiz-explorer"},
                {"Daily Spark", "Awarded for building a learning streak.", "badge-daily-spark"}
        };

        for (String[] item : badges) {
            String name = item[0];
            String description = item[1];
            String iconKey = item[2];
            badgeRepository.findByName(name).orElseGet(() -> {
                Badge badge = new Badge();
                badge.setName(name);
                badge.setDescription(description);
                badge.setIconKey(iconKey);
                return badgeRepository.save(badge);
            });
        }
    }

    private void seedSystemSettings() {
        Map<String, String[]> settings = new LinkedHashMap<>();
        Map<String, Boolean> editableSettings = new LinkedHashMap<>();
        addSetting(settings, editableSettings, "platform_name",
                "AI-Powered Interactive Chinese Learning Platform for Young Learners",
                "Displayed platform name.",
                false);
        addSetting(settings, editableSettings, "default_currency",
                "MYR",
                "Default currency for the Malaysian deployment context.",
                true);
        addSetting(settings, editableSettings, "platform_commission_rate",
                "0.20",
                "Editable platform commission rate represented as a decimal.",
                true);
        addSetting(settings, editableSettings, "ai_disclaimer_text",
                "AI-generated content may not always be accurate. Please review before use.",
                "Shown on AI-powered learning experiences.",
                true);
        addSetting(settings, editableSettings, "admin_email_notifications_enabled",
                "true",
                "Enable email notifications for admin workflows.",
                true);
        addSetting(settings, editableSettings, "admin_in_app_notifications_enabled",
                "true",
                "Enable in-app notifications for admin workflows.",
                true);
        addSetting(settings, editableSettings, "admin_daily_summary_enabled",
                "false",
                "Enable daily admin summary delivery.",
                true);
        addSetting(settings, editableSettings, "strict_content_filtering_enabled",
                "true",
                "Enable strict content filtering for safety-sensitive flows.",
                true);
        addSetting(settings, editableSettings, "sensitive_word_filter_enabled",
                "true",
                "Enable profanity and sensitive word filtering.",
                true);
        addSetting(settings, editableSettings, "ai_safety_check_enabled",
                "true",
                "Enable AI safety checks before AI guidance is shown.",
                true);

        settings.forEach((key, seed) -> systemSettingRepository.findBySettingKey(key).orElseGet(() -> {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(seed[0]);
            setting.setDescription(seed[1]);
            setting.setEditable(Boolean.TRUE.equals(editableSettings.get(key)));
            return systemSettingRepository.save(setting);
        }));
    }

    private void addSetting(
            Map<String, String[]> settings,
            Map<String, Boolean> editableSettings,
            String key,
            String value,
            String description,
            boolean editable) {
        settings.put(key, new String[] {value, description});
        editableSettings.put(key, editable);
    }

    private void seedDefaultAdmin() {
        if (defaultAdminEmail == null || defaultAdminEmail.isBlank()
                || defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            log.warn("Default admin credentials are not configured. Skipping default admin creation safely.");
            return;
        }

        String normalizedEmail = defaultAdminEmail.trim().toLowerCase(java.util.Locale.ROOT);

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

}
