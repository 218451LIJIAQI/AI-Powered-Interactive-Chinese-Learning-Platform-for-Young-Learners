package com.example.aichinese.service.ai;

import static com.example.aichinese.support.ConcurrentTransactions.pauseAfterLookup;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AiUsageQuotaCounterRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.LearnClock;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=",
        "DEFAULT_ADMIN_PASSWORD="
})
@ActiveProfiles("test")
class AiUsageLimiterTests {

    private static final String PLATFORM_ZONE = "Asia/Singapore";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-25T01:00:00Z");
    private static final LocalDateTime FIXED_LOCAL_DATE_TIME = LocalDateTime.of(2026, 5, 25, 1, 0);
    private static final LocalDate FIXED_USAGE_DAY = LocalDate.of(2026, 5, 25);
    private static final long MOCK_USER_ID = 99L;
    private static final int MINUTE_QUOTA = 2;
    private static final int DAILY_QUOTA = 1;
    private static final int GENEROUS_QUOTA = 10;
    private static final int STALE_COUNTER_VALUE = 99;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurrentAccount currentAccount;

    @MockitoSpyBean
    private AiUsageQuotaCounterRepository quotaCounterRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AiUsageLimiter springManagedLimiter;

    @Test
    void rejectsRequestsPastMinuteQuotaAndPersistsCounter() {
        AiUsageLimiter limiter = limiterAtFixedInstant(MINUTE_QUOTA, GENEROUS_QUOTA);
        User user = createUser("minute-quota");

        allow(limiter, user);
        allow(limiter, user);

        assertThatThrownBy(() -> allow(limiter, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Please wait a minute");

        AiUsageQuotaCounter counter = persistedCounter(user.getId());
        assertThat(counter.getMinuteCount()).isEqualTo(MINUTE_QUOTA);
        assertThat(counter.getDayCount()).isEqualTo(MINUTE_QUOTA);
    }

    @Test
    void maintainsIndependentQuotasPerStudentInSharedStore() {
        AiUsageLimiter limiter = limiterAtFixedInstant(DAILY_QUOTA, DAILY_QUOTA);
        User first = createUser("first-quota");
        User second = createUser("second-quota");

        allow(limiter, first);

        assertThatCode(() -> allow(limiter, second)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsPastDailyQuota() {
        AiUsageLimiter limiter = limiterAtFixedInstant(GENEROUS_QUOTA, DAILY_QUOTA);
        User user = createUser("daily-quota");

        allow(limiter, user);

        assertThatThrownBy(() -> allow(limiter, user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Daily AI request limit reached");
    }

    @Test
    void springManagedLimiterOpensItsOwnTransaction() {
        User user = createUser("spring-managed-quota");

        assertThatCode(() -> springManagedLimiter.checkProviderCallAllowed(user, null))
                .doesNotThrowAnyException();
    }

    /** SDD 5.6: per-user limits of 10 AI requests per minute and 100 per day. */
    @Test
    void springManagedLimiterUsesTheDocumentedQuota() {
        assertThat(springManagedLimiter.requestsPerMinute()).isEqualTo(10);
        assertThat(springManagedLimiter.requestsPerDay()).isEqualTo(100);
    }

    @Test
    void startsANewMinuteAndANewDayOnACounterFromAnEarlierWindow() {
        CurrentAccount mockAccount = mock(CurrentAccount.class);
        AiUsageQuotaCounterRepository mockQuotaRepository = mock(AiUsageQuotaCounterRepository.class);
        User user = user(MOCK_USER_ID);
        AiUsageQuotaCounter counter = staleCounter(user);
        when(mockAccount.lock(MOCK_USER_ID, user.getSessionVersion(), null)).thenReturn(user);
        when(mockQuotaRepository.findLockedByUserId(MOCK_USER_ID)).thenReturn(Optional.of(counter));
        AiUsageLimiter limiter = new AiUsageLimiter(fixedClock(), platformClock(), GENEROUS_QUOTA, GENEROUS_QUOTA,
                mockAccount, mockQuotaRepository);

        limiter.checkProviderCallAllowed(user, null);

        assertThat(counter.getMinuteStartedAt()).isEqualTo(FIXED_LOCAL_DATE_TIME);
        assertThat(counter.getMinuteCount()).isEqualTo(1);
        assertThat(counter.getUsageDay()).isEqualTo(FIXED_USAGE_DAY);
        assertThat(counter.getDayCount()).isEqualTo(1);
    }

    @Test
    void validatesBrowserSessionWhenSessionIdIsProvided() {
        CurrentAccount mockAccount = mock(CurrentAccount.class);
        AiUsageQuotaCounterRepository mockQuotaRepository = mock(AiUsageQuotaCounterRepository.class);
        User user = user(MOCK_USER_ID);
        AiUsageQuotaCounter counter = staleCounter(user);
        when(mockAccount.lock(MOCK_USER_ID, user.getSessionVersion(), "session-42")).thenReturn(user);
        when(mockQuotaRepository.findLockedByUserId(MOCK_USER_ID)).thenReturn(Optional.of(counter));
        AiUsageLimiter limiter = new AiUsageLimiter(fixedClock(), platformClock(), GENEROUS_QUOTA, GENEROUS_QUOTA,
                mockAccount, mockQuotaRepository);

        limiter.checkProviderCallAllowed(user, "session-42");

        verify(mockAccount).lock(MOCK_USER_ID, user.getSessionVersion(), "session-42");
        assertThat(counter.getDayCount()).isEqualTo(1);
    }

    /** The daily quota counts platform days, as streaks and daily goals do, not the days of the server's zone. */
    @Test
    void theDailyQuotaFollowsThePlatformDayNotTheServerZone() {
        // 20:00 in UTC (the server's zone here) is already 04:00 on the 26th in Singapore (the platform's zone).
        Clock lateEveningInUtc = Clock.fixed(Instant.parse("2026-05-25T20:00:00Z"), ZoneOffset.UTC);
        User user = createUser("platform-day");
        // Use the 25th's quota before Singapore reaches midnight.
        allow(limiterAtFixedInstant(GENEROUS_QUOTA, DAILY_QUOTA), user);
        AiUsageLimiter limiter = new AiUsageLimiter(lateEveningInUtc, platformClock(), GENEROUS_QUOTA, DAILY_QUOTA,
                currentAccount, quotaCounterRepository);

        assertThat(limiter.usedToday(user.getId())).isZero();
        assertThatCode(() -> allow(limiter, user)).doesNotThrowAnyException();
        AiUsageQuotaCounter counter = persistedCounter(user.getId());
        assertThat(counter.getUsageDay()).isEqualTo(LocalDate.of(2026, 5, 26));
        assertThat(counter.getDayCount()).isEqualTo(1);
        assertThat(limiter.usedToday(user.getId())).isEqualTo(1);
    }

    /**
     * Two users' first AI requests at the same moment both get their counter. The counter is looked up with a lock and
     * inserted when missing; at MySQL's default REPEATABLE READ that lookup locks the index gap where the row would go,
     * which the newest users share, so the second user's insert would wait for the first user's request (and two such
     * requests would deadlock). Here the first request pauses right after its lookup while the second one runs.
     */
    @Test
    void twoUsersFirstRequestsAtOnceAreBothCounted() throws Exception {
        User first = createUser("first-request");
        User second = createUser("first-request");

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(quotaCounterRepository).findLockedByUserId(anyLong());
            springManagedLimiter.checkProviderCallAllowed(first, null);
        }, () -> springManagedLimiter.checkProviderCallAllowed(second, null));

        assertThat(persistedCounter(first.getId()).getDayCount()).isEqualTo(1);
        assertThat(persistedCounter(second.getId()).getDayCount()).isEqualTo(1);
    }

    private AiUsageLimiter limiterAtFixedInstant(int requestsPerMinute, int requestsPerDay) {
        return new AiUsageLimiter(
                fixedClock(),
                platformClock(),
                requestsPerMinute,
                requestsPerDay,
                currentAccount,
                quotaCounterRepository);
    }

    private static Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }

    /** The platform zone of the test profile (FIXED_INSTANT is 09:00 there, on the same date). */
    private static LearnClock platformClock() {
        return new LearnClock(new PlatformProperties(PLATFORM_ZONE));
    }

    private AiUsageQuotaCounter persistedCounter(Long userId) {
        return transactionTemplate.execute(status -> quotaCounterRepository.findLockedByUserId(userId)
                .orElseThrow());
    }

    private User createUser(String prefix) {
        return transactionTemplate.execute(status -> {
            User user = user(null);
            user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
            user.setDisplayName(prefix);
            return userRepository.saveAndFlush(user);
        });
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return user;
    }

    /** A counter far over both quotas whose minute ended exactly now and whose day was yesterday. */
    private static AiUsageQuotaCounter staleCounter(User user) {
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(user);
        counter.setMinuteStartedAt(FIXED_LOCAL_DATE_TIME.minusMinutes(1));
        counter.setMinuteCount(STALE_COUNTER_VALUE);
        counter.setUsageDay(FIXED_USAGE_DAY.minusDays(1));
        counter.setDayCount(STALE_COUNTER_VALUE);
        return counter;
    }

    private void allow(AiUsageLimiter limiter, User user) {
        transactionTemplate.executeWithoutResult(status -> limiter.checkProviderCallAllowed(user, null));
    }
}
