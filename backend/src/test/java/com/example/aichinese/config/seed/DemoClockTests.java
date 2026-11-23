package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * The day-counted histories (DemoLearnSeeder's XP) keep every row on its platform day, even when the demo starts just
 * after midnight. The platform is in Singapore and the server in UTC, so stored times are 8 hours behind.
 */
class DemoClockTests {

    @Test
    void aTimeLaterTodayIsFiveMinutesAgo() {
        // Noon in Singapore.
        DemoClock clock = clockAt(Instant.parse("2026-11-14T04:00:00Z"));

        assertThat(clock.onDayAgo(0, 9)).isEqualTo(LocalDateTime.of(2026, 11, 14, 1, 0));
        assertThat(clock.onDayAgo(0, 18)).isEqualTo(LocalDateTime.of(2026, 11, 14, 3, 55));
        assertThat(clock.onDayAgo(2, 18)).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        // daysAgo moves such a time to the day before instead.
        assertThat(clock.daysAgo(0, 18, 0)).isEqualTo(LocalDateTime.of(2026, 11, 13, 10, 0));
    }

    @Test
    void justAfterMidnightATimeLaterTodayStaysOnToday() {
        // 00:02 on 15 November in Singapore: five minutes ago was still the 14th.
        DemoClock clock = clockAt(Instant.parse("2026-11-14T16:02:00Z"));

        LocalDateTime today = clock.onDayAgo(0, 9);

        assertThat(today).isEqualTo(LocalDateTime.of(2026, 11, 14, 16, 0));
        assertThat(clock.dayOf(today)).isEqualTo(LocalDate.of(2026, 11, 15)).isEqualTo(clock.today());
        assertThat(clock.dayOf(clock.onDayAgo(1, 9))).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void aPlatformWallTimeIsStoredAsTheSameMomentOnAServerInAnotherZone() {
        DemoClock utcServer = clockAt(Instant.parse("2026-11-14T04:00:00Z"));
        assertThat(utcServer.today()).isEqualTo(LocalDate.of(2026, 11, 14));
        // Three days ago at 10:00 in Singapore (a support reply and its audit entry): 02:00 UTC.
        assertThat(utcServer.daysAgo(3, 10, 0)).isEqualTo(LocalDateTime.of(2026, 11, 11, 2, 0));
        // Today at 20:15 is still ahead of noon: the day before, 12:15 UTC.
        assertThat(utcServer.daysAgo(0, 20, 15)).isEqualTo(LocalDateTime.of(2026, 11, 13, 12, 15));
        assertThat(utcServer.at(LocalDate.of(2026, 11, 7), LocalTime.of(20, 15)))
                .isEqualTo(LocalDateTime.of(2026, 11, 7, 12, 15));
        // 16:30 UTC is already the next day in Singapore.
        assertThat(utcServer.dayOf(LocalDateTime.of(2026, 11, 13, 16, 30))).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void dstSpringForwardGapDoesNotPlaceSeededTimeInTheFuture() {
        // 2026-03-08 03:10 EDT (07:10 UTC) in America/New_York: 02:30 springs forward to 03:30 EDT (07:30 UTC),
        // which is AFTER platformStart despite wall-clock 02:30 < 03:10.
        Instant start = Instant.parse("2026-03-08T07:10:00Z");
        DemoClock clock = new DemoClock(ZoneId.of("America/New_York"), ZoneOffset.UTC, start);

        assertThat(clock.daysAgo(0, 2, 30)).isBeforeOrEqualTo(clock.now());
        assertThat(clock.onDayAgo(0, 2)).isBeforeOrEqualTo(clock.now());
    }

    private static DemoClock clockAt(Instant instant) {
        return new DemoClock(ZoneId.of("Asia/Singapore"), ZoneOffset.UTC, instant);
    }
}
