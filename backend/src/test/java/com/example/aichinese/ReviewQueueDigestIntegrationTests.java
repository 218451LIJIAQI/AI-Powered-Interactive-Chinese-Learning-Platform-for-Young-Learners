package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.service.NoOpEmailService;
import com.example.aichinese.service.ScheduledJobRunner;
import com.example.aichinese.service.TransactionalEmailDispatcher.EmailDeliveryRequested;
import com.example.aichinese.service.admin.ReviewQueueDigestService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.ConcurrentTransactions;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The admins' daily summary e-mail against the database: from 8:00 platform time (Asia/Singapore in the tests), once
 * per platform day whoever runs it, with the numbers of the admin badges (AI reports, open support requests) and
 * absolute links, and none for an admin who switched it off. (With nothing waiting no e-mail goes out:
 * ReviewQueueDigestServiceTests, as the shared test database always has something waiting.)
 */
@RecordApplicationEvents
class ReviewQueueDigestIntegrationTests extends ApiIntegrationTest {

    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Singapore");
    private static final LocalDate DAY = LocalDate.of(2031, 3, 10);
    private static final String SUBJECT = "Your MandarinSpark daily summary";
    private static final String PREFERENCES = "/api/v1/users/me/notification-preferences";

    @Autowired
    private ReviewQueueDigestService digest;

    @Autowired
    private ScheduledJobRunner jobs;

    @Autowired
    private SystemSettingRepository settingRepository;

    @Autowired
    private EmailProperties emailProperties;

    @Autowired
    private ApplicationEvents events;

    @MockitoSpyBean
    private NoOpEmailService noOpEmailService;

    /** Every test starts and ends with no leftover digest watermark in the shared test database. */
    @BeforeEach
    @AfterEach
    void forgetTheLastRun() {
        settingRepository.findBySettingKey(ReviewQueueDigestService.LAST_RUN_DAY_KEY).ifPresent(settingRepository::delete);
        clearInvocations(noOpEmailService);
    }

    @Test
    void theAdminsGetOneEmailADayWithTheNumbersOfTheirBadges() throws Exception {
        sendSupportRequest("digest-learner");

        assertThat(digest.sendIfDue(at(DAY, 7, 59))).isZero();
        assertThat(summariesTo(ADMIN_EMAIL)).isEmpty();

        assertThat(digest.sendIfDue(at(DAY, 8, 0))).isPositive();

        List<EmailDeliveryRequested> sent = summariesTo(ADMIN_EMAIL);
        assertThat(sent).hasSize(1);
        JsonNode badges = api.data(mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", api.adminToken()))
                .andExpect(status().isOk())
                .andReturn());
        String body = sent.getFirst().body();
        assertQueueLine(body, "Support requests", badges.path("supportOpen").asLong(), "admin/support");
        assertQueueLine(body, "AI reports", badges.path("aiReports").asLong(), "admin/ai");
        assertThat(badges.path("supportOpen").asLong()).isPositive();
        assertThat(body).doesNotContain("Teacher applications", "Content reviews", "Support replies")
                .contains("Daily summary is switched on in Account > Email notifications");
        verify(noOpEmailService, timeout(5_000)).sendEmail(eq(ADMIN_EMAIL), eq(SUBJECT), eq(body));
        clearInvocations(noOpEmailService);

        // Later the same day, or after a restart (the day is kept in the database): no second e-mail.
        assertThat(digest.sendIfDue(at(DAY, 13, 0))).isZero();
        assertThat(settingRepository.findBySettingKey(ReviewQueueDigestService.LAST_RUN_DAY_KEY).orElseThrow()
                .getSettingValue()).isEqualTo("2031-03-10");
        assertThat(summariesTo(ADMIN_EMAIL)).hasSize(1);

        // The next platform day it comes again.
        assertThat(digest.sendIfDue(at(DAY.plusDays(1), 8, 1))).isPositive();
        List<EmailDeliveryRequested> afterNextDay = summariesTo(ADMIN_EMAIL);
        assertThat(afterNextDay).hasSize(2);
        verify(noOpEmailService, timeout(5_000)).sendEmail(eq(ADMIN_EMAIL), eq(SUBJECT), eq(afterNextDay.get(1).body()));
    }

    @Test
    void anAdminWhoSwitchedItOffGetsNone() throws Exception {
        sendSupportRequest("digest-off-learner");
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPut(PREFERENCES, adminToken, Map.of("reviewQueues", false))).andExpect(status().isOk());
        try {
            digest.sendIfDue(at(DAY, 9, 0));

            assertThat(summariesTo(ADMIN_EMAIL)).isEmpty();
        } finally {
            mockMvc.perform(api.jsonPut(PREFERENCES, adminToken, Map.of("reviewQueues", true)))
                    .andExpect(status().isOk());
        }
    }

    /** Two servers at the same minute: the conditional update of the day lets one of them send it. */
    @Test
    void twoRunsAtOnceSendTheDayOnce() throws Exception {
        sendSupportRequest("digest-race-learner");
        Clock clock = at(DAY, 8, 30);

        CyclicBarrier together = new CyclicBarrier(2);
        Callable<Integer> digestRun = () -> {
            together.await(10, TimeUnit.SECONDS);
            return digest.sendIfDue(clock);
        };
        int first;
        int second;
        ExecutorService runs = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = runs.submit(digestRun);
            Future<Integer> other = runs.submit(digestRun);
            first = one.get(20, TimeUnit.SECONDS);
            second = other.get(20, TimeUnit.SECONDS);
        } finally {
            runs.shutdownNow();
            runs.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(List.of(first, second)).containsOnlyOnce(0);
        assertThat(first + second).isPositive();
        // Await the single async email delivery so it cannot leak into a subsequent test.
        verify(noOpEmailService, timeout(5_000)).sendEmail(eq(ADMIN_EMAIL), eq(SUBJECT), anyString());
    }

    @Test
    void waitingRunnerTakesOverWhenTheFirstEverRunRollsBack() throws Exception {
        String key = ReviewQueueDigestService.LAST_RUN_DAY_KEY;
        Clock clock = at(DAY, 8, 0);
        int result = ConcurrentTransactions.withRowLock(jdbc, "system_settings",
                pause -> assertThatThrownBy(() -> jobs.runOnce(key, DAY, clock, () -> {
                    pause.run();
                    throw new IllegalStateException("Job failed");
                })).isInstanceOf(IllegalStateException.class).hasMessage("Job failed"),
                () -> jobs.runOnce(key, DAY, clock, () -> 7));

        assertThat(result).isEqualTo(7);
        assertThat(settingRepository.findValueByKey(key)).contains(DAY.toString());
        assertThat(jobs.runOnce(key, DAY, clock, () -> 7)).isZero();
    }

    /** A learner's "Report a problem": an open support request waits for the admins. */
    private void sendSupportRequest(String prefix) throws Exception {
        String token = api.registerStudent(uniqueEmail(prefix));
        mockMvc.perform(api.authMultipart("/api/v1/users/me/support", token)
                        .param("topic", "LEARNING").param("subject", "Lesson video won't play").param("message", "It stops."))
                .andExpect(status().isOk());
    }

    private List<EmailDeliveryRequested> summariesTo(String email) {
        return events.stream(EmailDeliveryRequested.class)
                .filter(sent -> sent.email().equals(email) && sent.subject().equals(SUBJECT))
                .toList();
    }

    private void assertQueueLine(String body, String name, long count, String page) {
        if (count > 0) {
            assertThat(body).contains(name + ": " + count + System.lineSeparator() + emailProperties.resolveFrontendUrl(page));
        } else {
            assertThat(body).doesNotContain(name + ":");
        }
    }

    /** The moment {@code hour:minute} of {@code day} in the platform zone. */
    private static Clock at(LocalDate day, int hour, int minute) {
        return Clock.fixed(day.atTime(hour, minute).atZone(PLATFORM_ZONE).toInstant(), ZoneOffset.UTC);
    }
}
