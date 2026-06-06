package com.example.aichinese.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
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
    private static final String CORE_POOL_SIZE_PROPERTY = "app.async.core-pool-size";
    private static final String MAX_POOL_SIZE_PROPERTY = "app.async.max-pool-size";
    private static final String QUEUE_CAPACITY_PROPERTY = "app.async.queue-capacity";

    @Bean(name = APPLICATION_TASK_EXECUTOR)
    Executor applicationTaskExecutor(
            @Value("${" + CORE_POOL_SIZE_PROPERTY + ":2}") int corePoolSize,
            @Value("${" + MAX_POOL_SIZE_PROPERTY + ":4}") int maxPoolSize,
            @Value("${" + QUEUE_CAPACITY_PROPERTY + ":100}") int queueCapacity) {
        validatePoolConfiguration(corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aichinese-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        // At saturation, deliver after commit on the caller thread rather than
        // losing security email or surfacing an error after data is committed.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    private static void validatePoolConfiguration(int corePoolSize, int maxPoolSize, int queueCapacity) {
        if (corePoolSize < 1) {
            throw new IllegalArgumentException(CORE_POOL_SIZE_PROPERTY + " must be at least 1.");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    MAX_POOL_SIZE_PROPERTY + " must be greater than or equal to " + CORE_POOL_SIZE_PROPERTY + ".");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(QUEUE_CAPACITY_PROPERTY + " must not be negative.");
        }
    }
}
