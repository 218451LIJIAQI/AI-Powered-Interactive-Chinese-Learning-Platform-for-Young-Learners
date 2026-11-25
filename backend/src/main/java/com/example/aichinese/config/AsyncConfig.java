package com.example.aichinese.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.aichinese.util.ExceptionLogUtils;

/**
 * Bounded executor for best-effort work that must not extend a user-facing
 * database transaction, such as outbound email delivery.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

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

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, params) -> {
            log.error("Async task {}.{} failed: {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ExceptionLogUtils.safeSummary(exception));
            ExceptionLogUtils.debugOrigin(log, exception);
        };
    }
}
