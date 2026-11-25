package com.example.aichinese.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.ScheduledJobRunner;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.TransactionalEmailDispatcher.ReviewQueue;
import com.example.aichinese.service.TransactionalEmailDispatcher.WaitingQueue;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.navigation.NavCountService;
import com.example.aichinese.service.navigation.NavCountService.AdminQueueCounts;

/**
 * The admins' daily summary e-mail: from 8:00 in the platform zone, once per platform day, only when AI reports or
 * support requests wait, with the badge numbers and absolute links. Recipient eligibility ({@code findActiveDigestAdmins})
 * is verified by {@code ReviewQueueDigestIntegrationTests}.
 */
class ReviewQueueDigestServiceTests {

    private static final String KEY = ReviewQueueDigestService.LAST_RUN_DAY_KEY;
    /** 08:05 on 10 March 2031 in Asia/Singapore (UTC+8). */
    private static final Instant MORNING = LocalDateTime.of(2031, 3, 10, 0, 5).toInstant(ZoneOffset.UTC);

    private final NavCountService navCountService = mock(NavCountService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SystemSettingRepository settingRepository = mock(SystemSettingRepository.class);
    private final TransactionalEmailDispatcher emailDispatcher = mock(TransactionalEmailDispatcher.class);
    private final ReviewQueueDigestService service = new ReviewQueueDigestService(
            navCountService,
            userRepository,
            new ScheduledJobRunner(settingRepository, new TransactionTemplate(mock(PlatformTransactionManager.class))),
            emailDispatcher,
            new EmailProperties(false, null, "https://app.example.com/reset-password"),
            new LearnClock(new PlatformProperties("Asia/Singapore")));

    @Test
    void nothingRunsBeforeEightInThePlatformZone() {
        // 07:59 in Singapore is 23:59 UTC the day before.
        assertThat(service.sendIfDue(clockAt(LocalDateTime.of(2031, 3, 9, 23, 59)))).isZero();

        verifyNoInteractions(settingRepository, navCountService, emailDispatcher);
    }

    @Test
    void eachWaitingQueueIsSentWithItsBadgeNumberAndAnAbsoluteLink() {
        lastRunDay("2031-03-09");
        claims("2031-03-10");
        when(navCountService.adminQueueCounts()).thenReturn(new AdminQueueCounts(3, 2));
        User admin = admin("admin@example.com");
        when(userRepository.findActiveDigestAdmins()).thenReturn(List.of(admin));

        assertThat(service.sendIfDue(Clock.fixed(MORNING, ZoneOffset.UTC))).isEqualTo(1);

        verify(emailDispatcher).enqueueReviewQueueDigest(admin, List.of(
                new WaitingQueue(ReviewQueue.AI_REPORTS, 3, "https://app.example.com/admin/ai"),
                new WaitingQueue(ReviewQueue.SUPPORT_REQUESTS, 2, "https://app.example.com/admin/support")));
    }

    @Test
    void emptyQueuesAreOmittedFromTheDigest() {
        lastRunDay("2031-03-09");
        claims("2031-03-10");
        when(navCountService.adminQueueCounts()).thenReturn(new AdminQueueCounts(0, 1));
        User recipient = admin("on@example.com");
        when(userRepository.findActiveDigestAdmins()).thenReturn(List.of(recipient));

        assertThat(service.sendIfDue(Clock.fixed(MORNING, ZoneOffset.UTC))).isEqualTo(1);

        verify(emailDispatcher).enqueueReviewQueueDigest(recipient, List.of(
                new WaitingQueue(ReviewQueue.SUPPORT_REQUESTS, 1, "https://app.example.com/admin/support")));
    }

    /** The day is looked at once, at 8:00: nothing waiting then means no e-mail that day. */
    @Test
    void nothingWaitingSendsNothingAndUsesUpTheDay() {
        lastRunDay("2031-03-09");
        claims("2031-03-10");
        when(navCountService.adminQueueCounts()).thenReturn(new AdminQueueCounts(0, 0));

        assertThat(service.sendIfDue(Clock.fixed(MORNING, ZoneOffset.UTC))).isZero();

        verify(settingRepository).advanceValue(eq(KEY), eq("2031-03-10"), any());
        verify(emailDispatcher, never()).enqueueReviewQueueDigest(any(), anyList());
        verify(userRepository, never()).findActiveDigestAdmins();
    }

    @Test
    void aDayThatRanAlreadySendsNothing() {
        lastRunDay("2031-03-10");

        assertThat(service.sendIfDue(Clock.fixed(MORNING.plusSeconds(3600), ZoneOffset.UTC))).isZero();

        verify(settingRepository, never()).advanceValue(any(), any(), any());
        verifyNoInteractions(navCountService, emailDispatcher);
    }

    /** Another server (or an overlapping run) moved the day on first: its e-mail went out, this one sends none. */
    @Test
    void theRunThatLosesTheClaimSendsNothing() {
        lastRunDay("2031-03-09");
        when(settingRepository.advanceValue(eq(KEY), eq("2031-03-10"), any())).thenReturn(0);

        assertThat(service.sendIfDue(Clock.fixed(MORNING, ZoneOffset.UTC))).isZero();

        verifyNoInteractions(navCountService, emailDispatcher);
    }

    @Test
    void theFirstRunEverCreatesTheDayRow() {
        when(settingRepository.findValueByKey(KEY)).thenReturn(Optional.empty());
        claims("2031-03-10");
        when(navCountService.adminQueueCounts()).thenReturn(new AdminQueueCounts(1, 0));
        when(userRepository.findActiveDigestAdmins()).thenReturn(List.of(admin("admin@example.com")));

        assertThat(service.sendIfDue(Clock.fixed(MORNING, ZoneOffset.UTC))).isEqualTo(1);

        verify(settingRepository).lockOrCreateEmpty(eq(KEY), any());
    }

    private void lastRunDay(String day) {
        when(settingRepository.findValueByKey(KEY)).thenReturn(Optional.of(day));
    }

    private void claims(String day) {
        when(settingRepository.advanceValue(eq(KEY), eq(day), any())).thenReturn(1);
    }

    private static Clock clockAt(LocalDateTime utc) {
        return Clock.fixed(utc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private static User admin(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }
}
