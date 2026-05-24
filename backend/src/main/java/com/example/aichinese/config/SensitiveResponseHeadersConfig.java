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
 * Adds {@code Cache-Control: no-store} (plus the older {@code Pragma: no-cache}
 * companion) to API responses that contain personally-identifiable or
 * privileged data, so that:
 *
 * <ul>
 *   <li>The browser disk cache and bfcache do not retain the JSON body of
 *       {@code /auth/me} or {@code /students/payments/**}, preventing a
 *       logged-out user from replaying it via the back button.</li>
 *   <li>Shared/proxy caches that ignore the JWT-only auth path do not
 *       accidentally serve another tenant's payment data.</li>
 * </ul>
 *
 * Using a {@link WebMvcConfigurer} interceptor (instead of inline
 * {@code ResponseEntity.ok().cacheControl(...)} on every controller) keeps
 * the policy declarative and prevents new PII endpoints from silently
 * shipping without the header. {@code AuthController#getCurrentUser} also
 * sets the header at the controller level for defense in depth; both
 * layers writing the same value is safe because Spring de-duplicates
 * Cache-Control on the response.
 */
@Configuration
public class SensitiveResponseHeadersConfig implements WebMvcConfigurer {

    /**
     * Path patterns whose authenticated role-scoped responses must never be
     * cached. Public catalogue/settings endpoints stay outside this policy.
     */
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

    /** {@code Cache-Control} value applied to sensitive responses. */
    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    /**
     * {@code Pragma} value kept for HTTP/1.0 proxy compatibility. The
     * {@code Cache-Control: no-store} header above is the normative
     * directive on every modern client.
     */
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

    /**
     * Stamps {@code Cache-Control: no-store} and {@code Pragma: no-cache}
     * onto matched responses during {@code postHandle}. Promoted to a
     * named static class (rather than an anonymous inner class) so stack
     * traces and JFR profiles surface a meaningful class name.
     */
    private static final class NoStoreHeaderInterceptor implements HandlerInterceptor {
        @Override
        public void postHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler,
                @Nullable ModelAndView modelAndView) {
            // postHandle runs before the response is committed, which is
            // when Spring still allows header mutation. Use setHeader
            // (not addHeader) so we never produce a duplicated header
            // line if another component already wrote one.
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
            response.setHeader(HttpHeaders.PRAGMA, PRAGMA_NO_CACHE);
        }
    }
}
