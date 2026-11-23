package com.example.aichinese.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor for best-effort work that must not extend a user-facing
 * database transaction, such as outbound email delivery.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    public static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";

    @Bean(name = APPLICATION_TASK_EXECUTOR)
    ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aichinese-async-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        // At saturation, deliver after commit on the caller thread rather than
        // losing security email or surfacing an error after data is committed.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}
