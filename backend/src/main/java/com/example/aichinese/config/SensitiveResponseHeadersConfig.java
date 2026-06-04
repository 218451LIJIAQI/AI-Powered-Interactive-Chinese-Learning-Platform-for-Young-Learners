package com.example.aichinese.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds no-store headers to sensitive API responses.
 */
@Configuration(proxyBeanMethods = false)
public class SensitiveResponseHeadersConfig implements WebMvcConfigurer {

    private static final String NO_STORE_AUTH_ME = "/api/v1/auth/me";
    private static final String NO_STORE_AUTH = "/api/v1/auth/**";
    private static final String NO_STORE_NOTIFICATIONS_LIST = "/api/v1/notifications";
    private static final String NO_STORE_NOTIFICATIONS = "/api/v1/notifications/**";
    private static final String NO_STORE_AI = "/api/v1/ai/**";
    private static final String NO_STORE_STUDENTS_LIST = "/api/v1/students";
    private static final String NO_STORE_STUDENTS = "/api/v1/students/**";
    private static final String NO_STORE_TEACHERS_LIST = "/api/v1/teachers";
    private static final String NO_STORE_TEACHERS = "/api/v1/teachers/**";
    private static final String NO_STORE_ADMIN_LIST = "/api/v1/admin";
    private static final String NO_STORE_ADMIN = "/api/v1/admin/**";
    private static final String NO_STORE_STUDENT_PAYMENTS = "/api/v1/students/payments/**";
    private static final String NO_STORE_STUDENT_REFUNDS = "/api/v1/students/refund-requests/**";
    private static final String NO_STORE_PAYMENTS = "/api/v1/payments/**";
    private static final String NO_STORE_ADMIN_AUDIT_LOGS_LIST = "/api/v1/admin/audit-logs";
    private static final String NO_STORE_ADMIN_AUDIT_LOGS = "/api/v1/admin/audit-logs/**";
    private static final String CACHEABLE_AI_AVAILABILITY = "/api/v1/ai/availability";

    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    private static final String PRAGMA_NO_CACHE = "no-cache";

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new NoStoreHeaderInterceptor())
                .addPathPatterns(
                        NO_STORE_AUTH_ME,
                        NO_STORE_AUTH,
                        NO_STORE_NOTIFICATIONS_LIST,
                        NO_STORE_NOTIFICATIONS,
                        NO_STORE_AI,
                        NO_STORE_STUDENTS_LIST,
                        NO_STORE_STUDENTS,
                        NO_STORE_TEACHERS_LIST,
                        NO_STORE_TEACHERS,
                        NO_STORE_ADMIN_LIST,
                        NO_STORE_ADMIN,
                        NO_STORE_STUDENT_PAYMENTS,
                        NO_STORE_STUDENT_REFUNDS,
                        NO_STORE_PAYMENTS,
                        NO_STORE_ADMIN_AUDIT_LOGS_LIST,
                        NO_STORE_ADMIN_AUDIT_LOGS)
                .excludePathPatterns(CACHEABLE_AI_AVAILABILITY);
    }

    private static final class NoStoreHeaderInterceptor implements HandlerInterceptor {
        @Override
        public void postHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler,
                @Nullable ModelAndView modelAndView) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
            response.setHeader(HttpHeaders.PRAGMA, PRAGMA_NO_CACHE);
        }
    }
}
