package com.example.aichinese.service.admin;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.ScheduledJobRunner;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.TransactionalEmailDispatcher.ReviewQueue;
import com.example.aichinese.service.TransactionalEmailDispatcher.WaitingQueue;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.navigation.NavCountService;
import com.example.aichinese.service.navigation.NavCountService.AdminQueueCounts;
import com.example.aichinese.util.ExceptionLogUtils;

import lombok.RequiredArgsConstructor;

/**
 * The admins' daily summary e-mail (Account › Email notifications › Daily summary, the {@code reviewQueues}
 * preference). On each platform day (APP_DEFAULT_TIME_ZONE, the day of LearnClock) the first run at or after 8:00 looks
 * at the queues once: when AI reports or support requests are waiting, every active admin who keeps the switch on gets
 * one e-mail (in the admin's language) with the numbers of the admin badges and a link to each queue; when nothing
 * waits, no e-mail goes out that day. The day of the last run is kept in system_settings and moved on with a
 * conditional update, so a restart or a second server never sends the day's e-mail twice.
 */
@Service
@RequiredArgsConstructor
public class ReviewQueueDigestService {

    private static final Logger log = LoggerFactory.getLogger(ReviewQueueDigestService.class);

    /** The system_settings row with the platform day (ISO date) of the last run; empty before the first one. */
    public static final String LAST_RUN_DAY_KEY = "review_queue_digest_last_day";
    /** From this time of the platform day the day's e-mail goes out. */
    private static final LocalTime SEND_FROM = LocalTime.of(8, 0);

    private final NavCountService navCountService;
    private final UserRepository userRepository;
    private final ScheduledJobRunner jobs;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final LearnClock learnClock;

    /** Every minute while background jobs run (app.scheduling.enabled). */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void sendIfDue() {
        try {
            sendIfDue(Clock.systemDefaultZone());
        } catch (Exception exception) {
            log.warn("Scheduled review queue digest failed: {}", ExceptionLogUtils.safeSummary(exception));
            ExceptionLogUtils.debugOrigin(log, exception);
        }
    }

    /** Runs the day's look at the queues if it is due; returns how many e-mails were queued. */
    public int sendIfDue(Clock clock) {
        LocalDateTime now = learnClock.dateTimeAt(clock.instant());
        if (now.toLocalTime().isBefore(SEND_FROM)) {
            return 0;
        }
        return jobs.runOnce(LAST_RUN_DAY_KEY, now.toLocalDate(), clock, () -> {
            List<WaitingQueue> waiting = waitingQueues(navCountService.adminQueueCounts());
            if (waiting.isEmpty()) {
                return 0;
            }
            List<User> admins = userRepository.findActiveDigestAdmins();
            admins.forEach(admin -> emailDispatcher.enqueueReviewQueueDigest(admin, waiting));
            return admins.size();
        });
    }

    /** The queues with something waiting, as the admin sidebar counts them, each with its page. */
    private List<WaitingQueue> waitingQueues(AdminQueueCounts counts) {
        List<WaitingQueue> waiting = new ArrayList<>();
        addIfWaiting(waiting, ReviewQueue.AI_REPORTS, counts.aiReports(), "admin/ai");
        addIfWaiting(waiting, ReviewQueue.SUPPORT_REQUESTS, counts.supportOpen(), "admin/support");
        return waiting;
    }

    private void addIfWaiting(List<WaitingQueue> waiting, ReviewQueue queue, long count, String page) {
        if (count > 0) {
            waiting.add(new WaitingQueue(queue, count, emailProperties.resolveFrontendUrl(page)));
        }
    }
}
