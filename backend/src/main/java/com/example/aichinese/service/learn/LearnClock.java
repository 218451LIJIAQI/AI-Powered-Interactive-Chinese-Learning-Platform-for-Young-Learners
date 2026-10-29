package com.example.aichinese.service.learn;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.PlatformProperties;

/**
 * "Today" for daily goals and streaks: the calendar day in the platform time zone (APP_DEFAULT_TIME_ZONE, Malaysia
 * by default), whatever zone the server runs in. Timestamps in the database are server-local, so the bounds of a day
 * are converted to server-local time.
 */
@Component
public class LearnClock {

    private final ZoneId platformZone;
    private final Clock clock;

    @Autowired
    public LearnClock(PlatformProperties platformProperties) {
        this(ZoneId.of(platformProperties.defaultTimeZone()), Clock.systemDefaultZone());
    }

    LearnClock(ZoneId platformZone, Clock clock) {
        this.platformZone = platformZone;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(platformZone));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Server-local start of a platform day. */
    public LocalDateTime startOf(LocalDate day) {
        return day.atStartOfDay(platformZone).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    /** The platform day of a server-local timestamp. */
    public LocalDate dayOf(LocalDateTime serverTime) {
        return serverTime.atZone(clock.getZone()).withZoneSameInstant(platformZone).toLocalDate();
    }
}
