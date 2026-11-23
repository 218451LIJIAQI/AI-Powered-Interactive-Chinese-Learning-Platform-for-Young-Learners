package com.example.aichinese.config.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.PlatformProperties;

/**
 * When the demo's events happened. The demo tells its story on the platform clock ("two days ago at 10:00", "a week ago
 * at 20:15", in APP_DEFAULT_TIME_ZONE like LearnClock), while timestamps are stored server-local. Every seeded time of
 * an event goes through here, so the rows that tell of the same moment (a SparkBot request and the plan it built, an
 * archived class and its audit entry) hold the same timestamp and fall on the same platform day, whatever zone the
 * server runs in.
 */
@Component
@Profile("demo & !prod & !production")
public class DemoClock {

    private final ZonedDateTime platformStart;
    private final ZoneId serverZone;

    @Autowired
    public DemoClock(PlatformProperties platformProperties) {
        this(ZoneId.of(platformProperties.defaultTimeZone()), ZoneId.systemDefault(), Instant.now());
    }

    /** All seeders share one reference instant, even if startup crosses midnight. */
    DemoClock(ZoneId platformZone, ZoneId serverZone, Instant start) {
        this.platformStart = start.atZone(platformZone);
        this.serverZone = serverZone;
    }

    /** The demo's reference instant as a stored server-local timestamp. */
    public LocalDateTime now() {
        return platformStart.withZoneSameInstant(serverZone).toLocalDateTime();
    }

    /** Today on the platform clock (LearnClock's today). */
    public LocalDate today() {
        return platformStart.toLocalDate();
    }

    /** The platform day of a stored (server-local) timestamp. */
    public LocalDate dayOf(LocalDateTime serverTime) {
        return serverTime.atZone(serverZone).withZoneSameInstant(platformStart.getZone()).toLocalDate();
    }

    /** {@code time} on the platform day {@code platformDay}, as stored: server-local. */
    public LocalDateTime at(LocalDate platformDay, LocalTime time) {
        return platformDay.atTime(time).atZone(platformStart.getZone()).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    /**
     * {@code days} platform days back at that platform wall-clock time, as stored (server-local); a time later today
     * than now moves to the day before, so nothing seeded lies in the future. Compares zoned instants so DST
     * spring-forward gaps in the platform zone never push a candidate past {@link #platformStart}.
     */
    public LocalDateTime daysAgo(int days, int hour, int minute) {
        LocalDate day = platformStart.toLocalDate().minusDays(days);
        LocalTime wallTime = LocalTime.of(hour, minute);
        ZonedDateTime candidate = day.atTime(wallTime).atZone(platformStart.getZone());
        if (candidate.isAfter(platformStart)) {
            candidate = day.minusDays(1).atTime(wallTime).atZone(platformStart.getZone());
        }
        return candidate.withZoneSameInstant(serverZone).toLocalDateTime();
    }

    /**
     * {@code hour}:00 on the platform day {@code days} back, as stored (server-local), and always on that day, for
     * histories that are counted by the day (the daily XP goal): a time later today than now becomes five minutes ago,
     * or today's midnight if five minutes ago was still yesterday.
     */
    public LocalDateTime onDayAgo(int days, int hour) {
        LocalDate day = platformStart.toLocalDate().minusDays(days);
        ZonedDateTime candidate = day.atTime(hour, 0).atZone(platformStart.getZone());
        if (candidate.isAfter(platformStart)) {
            ZonedDateTime recently = platformStart.minusMinutes(5);
            candidate = recently.toLocalDate().equals(day) ? recently : day.atStartOfDay(platformStart.getZone());
        }
        return candidate.withZoneSameInstant(serverZone).toLocalDateTime();
    }
}
