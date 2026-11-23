package com.example.aichinese.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.dto.admin.AdminAnalyticsResponse;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.support.SupportService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

class AdminOverviewServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    @Test
    void theRegistrationsChartUsesPlatformDaysNotServerDays() {
        // A platform zone at least 12 hours away from the server's: UTC-12 for a server at or east of UTC, else UTC+14.
        Instant fixedInstant = Instant.parse("2026-06-15T10:00:00Z");
        boolean platformBehindServer = ZoneId.systemDefault().getRules().getOffset(fixedInstant).getTotalSeconds() >= 0;
        LearnClock clock = new LearnClock(
                new PlatformProperties(platformBehindServer ? "Etc/GMT+12" : "Pacific/Kiritimati"),
                Clock.fixed(fixedInstant, ZoneOffset.UTC));
        LocalDate today = clock.today();
        // A sign-up on the platform's today that the server's clock (and the database) dates a day later or earlier:
        // offset by 2 hours so it never lands inside a 1-hour host DST fall-back overlap window.
        LocalDateTime signedUp = platformBehindServer
                ? clock.startOf(today.plusDays(1)).minusHours(2)
                : clock.startOf(today).plusHours(2);
        assertThat(signedUp.toLocalDate()).isNotEqualTo(today);
        when(userRepository.findCreatedAtBetween(any(), any())).thenReturn(List.of(signedUp));
        when(userRepository.countCurrentAccounts()).thenReturn(new AdminAnalyticsResponse.UserCounts(1, 1, 0));
        stubLearningQuery(Long.class, 0L);
        stubLearningQuery(AdminOverviewService.QuizStatistics.class, new AdminOverviewService.QuizStatistics(null, 0L));

        AdminAnalyticsResponse analytics = service(clock).analytics(7);

        assertThat(analytics.to()).isEqualTo(today);
        assertThat(analytics.from()).isEqualTo(today.minusDays(6));
        assertThat(analytics.registrations()).hasSize(7);
        assertThat(analytics.registrations().getLast())
                .isEqualTo(new AdminAnalyticsResponse.DayCount(today, 1));
        assertThat(analytics.newRegistrations()).isEqualTo(1);
        verify(userRepository).findCreatedAtBetween(clock.startOf(today.minusDays(6)), clock.startOf(today.plusDays(1)));
    }

    private <T> void stubLearningQuery(Class<T> resultType, T result) {
        @SuppressWarnings("unchecked")
        TypedQuery<T> query = mock(TypedQuery.class, RETURNS_SELF);
        when(query.getSingleResult()).thenReturn(result);
        when(entityManager.createQuery(anyString(), eq(resultType))).thenReturn(query);
    }

    private AdminOverviewService service(LearnClock clock) {
        return new AdminOverviewService(
                mock(SupportService.class),
                mock(AiResponseReportRepository.class),
                mock(AiUsageLogRepository.class),
                userRepository,
                mock(LessonRepository.class),
                mock(CoursePackageRepository.class),
                clock,
                entityManager);
    }

}
