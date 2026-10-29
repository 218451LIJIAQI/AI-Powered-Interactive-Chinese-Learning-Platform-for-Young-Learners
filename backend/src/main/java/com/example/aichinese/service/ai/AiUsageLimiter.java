package com.example.aichinese.service.ai;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AiUsageQuotaCounterRepository;
import com.example.aichinese.repository.UserRepository;

/**
 * Per-student cost guard applied immediately before a real text-provider call.
 * Quota state is stored in the database and updated under a row lock in a short transaction,
 * so concurrent requests cannot overspend the minute/day budget and a restart does not reset it.
 */
@Component
public class AiUsageLimiter {

    private static final String QUOTA_VERIFICATION_FAILED = "AI usage quota could not be verified.";
    private static final String MINUTE_LIMIT_REACHED =
            "AI request limit reached. Please wait a minute before trying again.";
    private static final String DAILY_LIMIT_REACHED =
            "Daily AI request limit reached. Please try again tomorrow.";

    private final Clock clock;
    private final int requestsPerMinute;
    private final int requestsPerDay;
    private final UserRepository userRepository;
    private final AiUsageQuotaCounterRepository quotaCounterRepository;

    @Autowired
    public AiUsageLimiter(
            @Value("${app.ai.rate-limit.requests-per-minute:10}") int requestsPerMinute,
            @Value("${app.ai.rate-limit.requests-per-day:100}") int requestsPerDay,
            UserRepository userRepository,
            AiUsageQuotaCounterRepository quotaCounterRepository) {
        this(Clock.systemDefaultZone(), requestsPerMinute, requestsPerDay, userRepository, quotaCounterRepository);
    }

    AiUsageLimiter(
            Clock clock,
            int requestsPerMinute,
            int requestsPerDay,
            UserRepository userRepository,
            AiUsageQuotaCounterRepository quotaCounterRepository) {
        if (requestsPerMinute < 1 || requestsPerDay < 1) {
            throw new IllegalArgumentException("AI rate limits must be positive.");
        }
        this.clock = clock;
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerDay = requestsPerDay;
        this.userRepository = userRepository;
        this.quotaCounterRepository = quotaCounterRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkProviderCallAllowed(Long userId) {
        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(TOO_MANY_REQUESTS, QUOTA_VERIFICATION_FAILED));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        AiUsageQuotaCounter counter = quotaCounterRepository.findLockedByUserId(userId)
                .orElseGet(() -> createCounter(user, now, today));

        rollForward(counter, now, today);
        enforceLimits(counter);
        counter.setMinuteCount(counter.getMinuteCount() + 1);
        counter.setDayCount(counter.getDayCount() + 1);
        quotaCounterRepository.save(counter);
    }

    /** Requests this user has sent to the provider today (blocked and unavailable requests do not count). */
    @Transactional(readOnly = true)
    public int usedToday(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return quotaCounterRepository.findByUserId(userId)
                .filter(counter -> today.equals(counter.getUsageDay()))
                .map(AiUsageQuotaCounter::getDayCount)
                .orElse(0);
    }

    public int requestsPerDay() {
        return requestsPerDay;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    private void enforceLimits(AiUsageQuotaCounter counter) {
        if (counter.getMinuteCount() >= requestsPerMinute) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, MINUTE_LIMIT_REACHED);
        }
        if (counter.getDayCount() >= requestsPerDay) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, DAILY_LIMIT_REACHED);
        }
    }

    private AiUsageQuotaCounter createCounter(User user, LocalDateTime now, LocalDate today) {
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(user);
        counter.setMinuteStartedAt(now);
        counter.setMinuteCount(0);
        counter.setUsageDay(today);
        counter.setDayCount(0);
        return counter;
    }

    private static void rollForward(AiUsageQuotaCounter counter, LocalDateTime now, LocalDate today) {
        if (counter.getMinuteStartedAt() == null
                || !counter.getMinuteStartedAt().plusMinutes(1).isAfter(now)) {
            counter.setMinuteStartedAt(now);
            counter.setMinuteCount(0);
        }

        if (counter.getUsageDay() == null || !counter.getUsageDay().equals(today)) {
            counter.setUsageDay(today);
            counter.setDayCount(0);
        }
    }
}
