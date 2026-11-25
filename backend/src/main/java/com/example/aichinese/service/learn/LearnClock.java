package com.example.aichinese.service.learn;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.PlatformProperties;

/**
 * "Today" for daily goals, streaks, the plan and the daily AI quota: the calendar day in the platform time zone
 * (APP_DEFAULT_TIME_ZONE, Asia/Singapore by default), whatever zone the server runs in. Timestamps in the database are
 * server-local, so the bounds of a day are converted to server-local time.
 */
@Component
public class LearnClock {

    private final ZoneId platformZone;
    private final Clock clock;

    @Autowired
    public LearnClock(PlatformProperties platformProperties) {
        this(platformProperties, Clock.systemUTC());
    }

    public LearnClock(PlatformProperties platformProperties, Clock clock) {
        this.platformZone = ZoneId.of(platformProperties.defaultTimeZone());
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), platformZone);
    }

    /** The server-local date and time at {@code instant} (for database timestamp columns matching {@link #startOf}). */
    public LocalDateTime serverTimeAt(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /** The platform day at {@code instant}. */
    public LocalDate dayAt(Instant instant) {
        return LocalDate.ofInstant(instant, platformZone);
    }

    /** The platform date and time at {@code instant}. */
    public LocalDateTime dateTimeAt(Instant instant) {
        return LocalDateTime.ofInstant(instant, platformZone);
    }

    /** Server-local start of the first of the last 7 platform days, today included. */
    public LocalDateTime weekStart() {
        return startOf(today().minusDays(6));
    }

    /** Server-local start of a platform day. */
    public LocalDateTime startOf(LocalDate day) {
        return day.atStartOfDay(platformZone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** The platform day of a server-local timestamp. */
    public LocalDate dayOf(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(platformZone).toLocalDate();
    }

    /** The platform-zone offset timestamp of a server-local timestamp (e.g. for CSV exports matching platform-day filters). */
    public OffsetDateTime platformOffsetDateTimeOf(LocalDateTime serverTime) {
        return serverTime == null
                ? null
                : serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(platformZone).toOffsetDateTime();
    }
}
