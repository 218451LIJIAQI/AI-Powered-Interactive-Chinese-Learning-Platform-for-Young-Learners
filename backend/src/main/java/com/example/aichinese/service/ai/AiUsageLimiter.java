package com.example.aichinese.service.ai;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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
 * Quota state is stored in the database and updated inside a short transaction,
 * so multiple backend instances share the same minute/day budget.
 */
@Component
public class AiUsageLimiter {

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
        this(Clock.systemUTC(), requestsPerMinute, requestsPerDay, userRepository, quotaCounterRepository);
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
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerDay = requestsPerDay;
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.quotaCounterRepository = Objects.requireNonNull(quotaCounterRepository, "quotaCounterRepository");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkProviderCallAllowed(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "AI usage quota could not be verified.");
        }

        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(TOO_MANY_REQUESTS, "AI usage quota could not be verified."));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        AiUsageQuotaCounter counter = quotaCounterRepository.findLockedByUserId(userId)
                .orElseGet(() -> createCounter(user, now, today));

        rollForward(counter, now, today);
        if (counter.getMinuteCount() >= requestsPerMinute) {
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "AI request limit reached. Please wait a minute before trying again.");
        }
        if (counter.getDayCount() >= requestsPerDay) {
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "Daily AI request limit reached. Please try again tomorrow.");
        }

        counter.setMinuteCount(counter.getMinuteCount() + 1);
        counter.setDayCount(counter.getDayCount() + 1);
        quotaCounterRepository.save(counter);
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
        if (!counter.getMinuteStartedAt().plus(Duration.ofMinutes(1)).isAfter(now)) {
            counter.setMinuteStartedAt(now);
            counter.setMinuteCount(0);
        }

        if (!counter.getUsageDay().equals(today)) {
            counter.setUsageDay(today);
            counter.setDayCount(0);
        }
    }

}
