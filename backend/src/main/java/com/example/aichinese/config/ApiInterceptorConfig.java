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
 * Registers the API interceptors in order: {@code Cache-Control: no-store} on private API routes first, so a 403
 * from the central teacher-approval guard that follows still carries it.
 */
@Configuration(proxyBeanMethods = false)
public class ApiInterceptorConfig implements WebMvcConfigurer {

    private static final String[] NO_STORE_PATTERNS = {
            "/api/v1/auth/**",
            "/api/v1/users/**",
            "/api/v1/notifications",
            "/api/v1/notifications/**",
            "/api/v1/ai/**",
            "/api/v1/students",
            "/api/v1/students/**",
            "/api/v1/teachers",
            "/api/v1/teachers/**",
            "/api/v1/admin",
            "/api/v1/admin/**"
    };

    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    private static final String PRAGMA_NO_CACHE = "no-cache";

    private final ApprovedTeacherInterceptor approvedTeacherInterceptor;

    public ApiInterceptorConfig(ApprovedTeacherInterceptor approvedTeacherInterceptor) {
        this.approvedTeacherInterceptor = approvedTeacherInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new NoStoreHeaderInterceptor())
                .addPathPatterns(NO_STORE_PATTERNS);
        registry.addInterceptor(approvedTeacherInterceptor)
                .addPathPatterns(ApprovedTeacherInterceptor.TEACHER_API_PATTERN);
    }

    private static final class NoStoreHeaderInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
            response.setHeader(HttpHeaders.PRAGMA, PRAGMA_NO_CACHE);
            return true;
        }
    }
}
