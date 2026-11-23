package com.example.aichinese.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registers the API interceptor that sends {@code Cache-Control: no-store} on the private API routes.
 */
@Configuration(proxyBeanMethods = false)
public class ApiInterceptorConfig implements WebMvcConfigurer {

    /** A {@code /**} pattern also matches its bare path, such as {@code /api/v1/notifications}. */
    private static final String[] NO_STORE_PATTERNS = {
            "/api/v1/auth/**",
            "/api/v1/users/**",
            "/api/v1/notifications/**",
            "/api/v1/ai/**",
            "/api/v1/students/**",
            "/api/v1/admin/**"
    };

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new NoStoreHeaderInterceptor())
                .addPathPatterns(NO_STORE_PATTERNS);
    }

    private static final class NoStoreHeaderInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            return true;
        }
    }
}
