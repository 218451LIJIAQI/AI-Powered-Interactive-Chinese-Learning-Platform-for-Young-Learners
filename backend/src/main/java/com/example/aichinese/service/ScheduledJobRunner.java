package com.example.aichinese.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.IntSupplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.repository.SystemSettingRepository;

import lombok.RequiredArgsConstructor;

/** Records one successful run of each scheduled job per platform date or week. */
@Component
@RequiredArgsConstructor
public class ScheduledJobRunner {

    private final SystemSettingRepository settingRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Runs {@code job} once for {@code period} (a platform day, or a week by its Monday), however many restarts and
     * servers try: the job runs in the transaction that claims the period in the system_settings row {@code key}, so a
     * failed job leaves the period unclaimed for the next try. Returns the job's count; 0 when the period was handled.
     */
    public int runOnce(String key, LocalDate period, Clock clock, IntSupplier job) {
        String day = period.toString();
        Optional<String> lastRun = settingRepository.findValueByKey(key);
        if (lastRun.isPresent() && lastRun.get().compareTo(day) >= 0) {
            return 0;
        }
        LocalDateTime serverNow = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        Integer result = transactionTemplate.execute(status -> {
            if (lastRun.isEmpty()) {
                settingRepository.lockOrCreateEmpty(key, serverNow);
            }
            return settingRepository.advanceValue(key, day, serverNow) == 0 ? 0 : job.getAsInt();
        });
        return result == null ? 0 : result;
    }

    /**
     * Claims {@code period} in a short transaction and releases the {@code system_settings} row lock immediately, so
     * multi-batch jobs can process recipients in bounded chunks outside the settings lock.
     */
    public boolean claimPeriod(String key, LocalDate period, Clock clock) {
        return runOnce(key, period, clock, () -> 1) > 0;
    }
}
