package com.example.aichinese.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Small in-process cache for stable reference and platform-setting reads.
 * This deployment is currently single-instance; a multi-instance deployment
 * should replace this with a shared cache or event-driven invalidation.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ADMIN_PLATFORM_SETTINGS = "adminPlatformSettings";
    public static final String PUBLIC_PLATFORM_SETTINGS = "publicPlatformSettings";
    public static final String DEFAULT_CURRENCY = "defaultCurrency";
    public static final String COMMISSION_RATE = "commissionRate";
    public static final String PUBLIC_LEVELS = "publicLevels";
    public static final String PUBLIC_CATEGORIES = "publicCategories";
    public static final String SENSITIVE_WORD_FILTER_ENABLED = "sensitiveWordFilterEnabled";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                ADMIN_PLATFORM_SETTINGS,
                PUBLIC_PLATFORM_SETTINGS,
                DEFAULT_CURRENCY,
                COMMISSION_RATE,
                PUBLIC_LEVELS,
                PUBLIC_CATEGORIES,
                SENSITIVE_WORD_FILTER_ENABLED);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(128)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());
        return cacheManager;
    }
}
