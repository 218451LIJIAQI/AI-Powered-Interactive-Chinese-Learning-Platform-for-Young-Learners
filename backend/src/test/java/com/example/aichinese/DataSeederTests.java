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
import com.example.aichinese.repository.SubscriptionPlanRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=",
        "DEFAULT_ADMIN_PASSWORD="
})
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
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSeedEssentialReferenceDataWithoutDefaultAdmin() {
        assertThat(roleRepository.count()).isEqualTo(3);
        assertThat(learningLevelRepository.count()).isEqualTo(3);
        assertThat(learningCategoryRepository.count()).isEqualTo(15);
        assertThat(badgeRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(subscriptionPlanRepository.count()).isEqualTo(4);
        assertThat(systemSettingRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(userRepository.count()).isZero();
    }
}
