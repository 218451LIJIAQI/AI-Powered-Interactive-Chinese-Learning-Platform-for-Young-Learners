package com.example.aichinese.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
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

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-25T01:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_DATE_TIME = LocalDateTime.of(2026, 5, 25, 1, 0);
    private static final LocalDate FIXED_USAGE_DAY = LocalDate.of(2026, 5, 25);
    private static final long MOCK_USER_ID = 99L;
    private static final int MINUTE_QUOTA = 2;
    private static final int DAILY_QUOTA = 1;
    private static final int GENEROUS_QUOTA = 10;
    private static final int STALE_COUNTER_VALUE = 99;
    private static final String MINUTE_QUOTA_PREFIX = "minute-quota";
    private static final String FIRST_QUOTA_PREFIX = "first-quota";
    private static final String SECOND_QUOTA_PREFIX = "second-quota";
    private static final String DAILY_QUOTA_PREFIX = "daily-quota";
    private static final String SPRING_MANAGED_QUOTA_PREFIX = "spring-managed-quota";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String EMAIL_DOMAIN = "@example.com";
    private static final String MINUTE_LIMIT_MESSAGE = "Please wait a minute";
    private static final String DAILY_LIMIT_MESSAGE = "Daily AI request limit reached";

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
        AiUsageLimiter limiter = limiterAtFixedInstant(MINUTE_QUOTA, GENEROUS_QUOTA);
        User user = createUser(MINUTE_QUOTA_PREFIX);

        allow(limiter, user.getId());
        allow(limiter, user.getId());

        assertThatThrownBy(() -> allow(limiter, user.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(MINUTE_LIMIT_MESSAGE);

        AiUsageQuotaCounter counter = persistedCounter(user.getId());
        assertThat(counter.getMinuteCount()).isEqualTo(MINUTE_QUOTA);
        assertThat(counter.getDayCount()).isEqualTo(MINUTE_QUOTA);
    }

    @Test
    void maintainsIndependentQuotasPerStudentInSharedStore() {
        AiUsageLimiter limiter = limiterAtFixedInstant(DAILY_QUOTA, DAILY_QUOTA);
        User first = createUser(FIRST_QUOTA_PREFIX);
        User second = createUser(SECOND_QUOTA_PREFIX);

        allow(limiter, first.getId());

        assertThatCode(() -> allow(limiter, second.getId())).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsPastDailyQuota() {
        AiUsageLimiter limiter = limiterAtFixedInstant(GENEROUS_QUOTA, DAILY_QUOTA);
        User user = createUser(DAILY_QUOTA_PREFIX);

        allow(limiter, user.getId());

        assertThatThrownBy(() -> allow(limiter, user.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(DAILY_LIMIT_MESSAGE);
    }

    @Test
    void springManagedLimiterOpensItsOwnTransaction() {
        User user = createUser(SPRING_MANAGED_QUOTA_PREFIX);

        assertThatCode(() -> springManagedLimiter.checkProviderCallAllowed(user.getId()))
                .doesNotThrowAnyException();
    }

    /** SDD 5.6: per-user limits of 10 AI requests per minute and 100 per day. */
    @Test
    void springManagedLimiterUsesTheDocumentedQuota() {
        assertThat(ReflectionTestUtils.getField(springManagedLimiter, "requestsPerMinute"))
                .isEqualTo(10);
        assertThat(ReflectionTestUtils.getField(springManagedLimiter, "requestsPerDay"))
                .isEqualTo(100);
    }

    @Test
    void repairsMissingWindowFieldsOnExistingCounter() {
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageQuotaCounterRepository quotaCounterRepository = mock(AiUsageQuotaCounterRepository.class);
        User user = user(MOCK_USER_ID);
        AiUsageQuotaCounter counter = staleCounter(user);
        when(userRepository.findLockedById(MOCK_USER_ID)).thenReturn(Optional.of(user));
        when(quotaCounterRepository.findLockedByUserId(MOCK_USER_ID)).thenReturn(Optional.of(counter));
        when(quotaCounterRepository.save(any(AiUsageQuotaCounter.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AiUsageLimiter limiter = mockBackedLimiter(userRepository, quotaCounterRepository);

        limiter.checkProviderCallAllowed(MOCK_USER_ID);

        assertThat(counter.getMinuteStartedAt()).isEqualTo(FIXED_LOCAL_DATE_TIME);
        assertThat(counter.getMinuteCount()).isEqualTo(1);
        assertThat(counter.getUsageDay()).isEqualTo(FIXED_USAGE_DAY);
        assertThat(counter.getDayCount()).isEqualTo(1);
    }

    private AiUsageLimiter limiterAtFixedInstant(int requestsPerMinute, int requestsPerDay) {
        return new AiUsageLimiter(
                fixedClock(),
                requestsPerMinute,
                requestsPerDay,
                userRepository,
                quotaCounterRepository);
    }

    private static AiUsageLimiter mockBackedLimiter(
            UserRepository userRepository,
            AiUsageQuotaCounterRepository quotaCounterRepository) {
        return new AiUsageLimiter(
                fixedClock(),
                GENEROUS_QUOTA,
                GENEROUS_QUOTA,
                userRepository,
                quotaCounterRepository);
    }

    private static Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }

    private AiUsageQuotaCounter persistedCounter(Long userId) {
        return transactionTemplate.execute(status -> quotaCounterRepository.findLockedByUserId(userId)
                .orElseThrow());
    }

    private User createUser(String prefix) {
        return transactionTemplate.execute(status -> {
            User user = user(null);
            user.setEmail(prefix + "-" + UUID.randomUUID() + EMAIL_DOMAIN);
            user.setDisplayName(prefix);
            return userRepository.saveAndFlush(user);
        });
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return user;
    }

    private static AiUsageQuotaCounter staleCounter(User user) {
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(user);
        counter.setMinuteStartedAt(null);
        counter.setMinuteCount(STALE_COUNTER_VALUE);
        counter.setUsageDay(null);
        counter.setDayCount(STALE_COUNTER_VALUE);
        return counter;
    }

    private void allow(AiUsageLimiter limiter, Long userId) {
        transactionTemplate.executeWithoutResult(status -> limiter.checkProviderCallAllowed(userId));
    }
}
