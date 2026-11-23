package com.example.aichinese.service.ai;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AiUsageQuotaCounterRepository;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.LearnClock;

/**
 * Per-user cost guard applied immediately before the provider is called.
 * The request's session is rechecked before reserving quota. Quota state is stored in the database
 * and updated under a row lock in a short transaction,
 * so concurrent requests cannot overspend the minute/day budget and a restart does not reset it.
 * The day of the daily quota is the platform day of {@link LearnClock}, like streaks and daily goals.
 */
@Component
public class AiUsageLimiter {

    private final Clock clock;
    private final LearnClock learnClock;
    private final int requestsPerMinute;
    private final int requestsPerDay;
    private final CurrentAccount currentAccount;
    private final AiUsageQuotaCounterRepository quotaCounterRepository;

    @Autowired
    public AiUsageLimiter(
            AiProperties aiProperties,
            CurrentAccount currentAccount,
            AiUsageQuotaCounterRepository quotaCounterRepository,
            LearnClock learnClock) {
        this(Clock.systemDefaultZone(), learnClock,
                aiProperties.getRateLimit().getRequestsPerMinute(),
                aiProperties.getRateLimit().getRequestsPerDay(),
                currentAccount, quotaCounterRepository);
    }

    AiUsageLimiter(
            Clock clock,
            LearnClock learnClock,
            int requestsPerMinute,
            int requestsPerDay,
            CurrentAccount currentAccount,
            AiUsageQuotaCounterRepository quotaCounterRepository) {
        if (requestsPerMinute < 1 || requestsPerDay < 1) {
            throw new IllegalArgumentException("AI rate limits must be positive.");
        }
        this.clock = clock;
        this.learnClock = learnClock;
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerDay = requestsPerDay;
        this.currentAccount = currentAccount;
        this.quotaCounterRepository = quotaCounterRepository;
    }

    /**
     * Read committed: a user's first request inserts the counter its locked lookup did not find, and at REPEATABLE READ
     * that lookup would lock the index gap the newest users' counters share, so two users' first requests would
     * deadlock. The user's own row lock keeps one user's requests in turn.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void checkProviderCallAllowed(User requestUser, String sessionId) {
        User user = currentAccount.lock(requestUser.getId(), requestUser.getSessionVersion(), sessionId);
        // The minute window stays in server-local time (as stored); the day is the platform day.
        Instant instant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(instant, clock.getZone());
        LocalDate today = learnClock.dayAt(instant);
        AiUsageQuotaCounter counter = quotaCounterRepository.findLockedByUserId(user.getId())
                .orElseGet(() -> createCounter(user, now, today));

        rollForward(counter, now, today);
        enforceLimits(counter);
        counter.setMinuteCount(counter.getMinuteCount() + 1);
        counter.setDayCount(counter.getDayCount() + 1);
        quotaCounterRepository.save(counter);
    }

    /** Provider requests reserved for this user during the current platform day. */
    @Transactional(readOnly = true)
    public int usedToday(Long userId) {
        LocalDate today = learnClock.dayAt(clock.instant());
        return quotaCounterRepository.findDayCount(userId, today).orElse(0);
    }

    public int requestsPerDay() {
        return requestsPerDay;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    private void enforceLimits(AiUsageQuotaCounter counter) {
        if (counter.getDayCount() >= requestsPerDay) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "Daily AI request limit reached. Please try again tomorrow.");
        }
        if (counter.getMinuteCount() >= requestsPerMinute) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "AI request limit reached. Please wait a minute before trying again.");
        }
    }

    private AiUsageQuotaCounter createCounter(User user, LocalDateTime now, LocalDate today) {
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(user);
        counter.setMinuteStartedAt(now);
        counter.setUsageDay(today);
        return counter;
    }

    private static void rollForward(AiUsageQuotaCounter counter, LocalDateTime now, LocalDate today) {
        if (!counter.getMinuteStartedAt().plusMinutes(1).isAfter(now) || now.isBefore(counter.getMinuteStartedAt())) {
            counter.setMinuteStartedAt(now);
            counter.setMinuteCount(0);
        }

        if (!counter.getUsageDay().equals(today)) {
            counter.setUsageDay(today);
            counter.setDayCount(0);
        }
    }
}
