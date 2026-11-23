package com.example.aichinese.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs: the admins' daily summary e-mail (ReviewQueueDigestService) and the learners' daily-goal and
 * mistakes reminders (LearnerReminderJobs). {@code app.scheduling.enabled=false} (APP_SCHEDULING_ENABLED) switches
 * them off; the tests do, and call the jobs with a fixed clock instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
