package com.example.aichinese.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AiUsageQuotaCounterRepository;
import com.example.aichinese.repository.UserRepository;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=",
        "DEFAULT_ADMIN_PASSWORD="
})
@ActiveProfiles("test")
class AiUsageLimiterTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiUsageQuotaCounterRepository quotaCounterRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AiUsageLimiter springManagedLimiter;

    @Test
    void rejectsRequestsPastMinuteQuotaAndPersistsCounter() {
        AiUsageLimiter limiter = limiterAt("2026-05-25T01:00:00Z", 2, 10);
        User user = createUser("minute-quota");

        allow(limiter, user.getId());
        allow(limiter, user.getId());

        assertThatThrownBy(() -> allow(limiter, user.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Please wait a minute");

        AiUsageQuotaCounter counter = transactionTemplate.execute(status -> quotaCounterRepository.findLockedByUserId(user.getId()).orElseThrow());
        assertThat(counter.getMinuteCount()).isEqualTo(2);
        assertThat(counter.getDayCount()).isEqualTo(2);
    }

    @Test
    void maintainsIndependentQuotasPerStudentInSharedStore() {
        AiUsageLimiter limiter = limiterAt("2026-05-25T01:00:00Z", 1, 1);
        User first = createUser("first-quota");
        User second = createUser("second-quota");

        allow(limiter, first.getId());

        assertThatCode(() -> allow(limiter, second.getId())).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsPastDailyQuota() {
        AiUsageLimiter limiter = limiterAt("2026-05-25T01:00:00Z", 10, 1);
        User user = createUser("daily-quota");

        allow(limiter, user.getId());

        assertThatThrownBy(() -> allow(limiter, user.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Daily AI request limit reached");
    }

    @Test
    void springManagedLimiterOpensItsOwnTransaction() {
        User user = createUser("spring-managed-quota");

        assertThatCode(() -> springManagedLimiter.checkProviderCallAllowed(user.getId())).doesNotThrowAnyException();
    }

    @Test
    void repairsMissingWindowFieldsOnExistingCounter() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageQuotaCounterRepository quotaCounterRepository = mock(AiUsageQuotaCounterRepository.class);
        User user = new User();
        user.setId(99L);
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(user);
        counter.setMinuteStartedAt(null);
        counter.setMinuteCount(99);
        counter.setUsageDay(null);
        counter.setDayCount(99);
        when(userRepository.findLockedById(99L)).thenReturn(Optional.of(user));
        when(quotaCounterRepository.findLockedByUserId(99L)).thenReturn(Optional.of(counter));
        when(quotaCounterRepository.save(any(AiUsageQuotaCounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AiUsageLimiter limiter = new AiUsageLimiter(
                Clock.fixed(Instant.parse("2026-05-25T01:00:00Z"), ZoneOffset.UTC),
                10,
                10,
                userRepository,
                quotaCounterRepository);

        limiter.checkProviderCallAllowed(99L);

        assertThat(counter.getMinuteStartedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 5, 25, 1, 0));
        assertThat(counter.getMinuteCount()).isEqualTo(1);
        assertThat(counter.getUsageDay()).isEqualTo(java.time.LocalDate.of(2026, 5, 25));
        assertThat(counter.getDayCount()).isEqualTo(1);
    }

    private AiUsageLimiter limiterAt(String instant, int requestsPerMinute, int requestsPerDay) {
        return new AiUsageLimiter(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                requestsPerMinute,
                requestsPerDay,
                userRepository,
                quotaCounterRepository);
    }

    private User createUser(String prefix) {
        return transactionTemplate.execute(status -> {
            User user = new User();
            user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
            user.setDisplayName(prefix);
            user.setPasswordHash("password-hash");
            user.setActive(true);
            return userRepository.saveAndFlush(user);
        });
    }

    private void allow(AiUsageLimiter limiter, Long userId) {
        transactionTemplate.executeWithoutResult(status -> limiter.checkProviderCallAllowed(userId));
    }
}
