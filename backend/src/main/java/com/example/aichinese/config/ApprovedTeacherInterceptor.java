package com.example.aichinese.config;

import java.util.List;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.TeacherContentAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Central teacher-approval guard (D13, FR-TVER-05, BR-03). Every {@code /api/v1/teachers/**}
 * endpoint requires an APPROVED, active teacher except the dashboard shell, the teacher's own
 * profile and the verification workflow, which pending and rejected teachers still need.
 * Role checks (TEACHER) stay in {@link SecurityConfig}; this guard adds the status check in one
 * place instead of scattered per-service checks.
 */
@Component
public class ApprovedTeacherInterceptor implements HandlerInterceptor {

    static final String TEACHER_API_PATTERN = "/api/v1/teachers/**";

    /** Endpoints open to PENDING / REJECTED teachers. */
    static final List<String> OPEN_TEACHER_PATHS = List.of(
            "/api/v1/teachers/me",
            "/api/v1/teachers/me/profile",
            "/api/v1/teachers/dashboard",
            "/api/v1/teachers/verification-status",
            "/api/v1/teachers/verification-documents",
            "/api/v1/teachers/verification-documents/*",
            "/api/v1/teachers/resubmit-verification");

    /** Learner-facing features (roster, feedback, analytics, AI drafts) use the generic feature message. */
    static final List<String> LEARNER_FEATURE_PATHS = List.of(
            "/api/v1/teachers/students",
            "/api/v1/teachers/students/**",
            "/api/v1/teachers/feedback",
            "/api/v1/teachers/feedback/**",
            "/api/v1/teachers/practice-submissions",
            "/api/v1/teachers/practice-submissions/**",
            "/api/v1/teachers/analytics",
            "/api/v1/teachers/ai/**");

    private final TeacherContentAccessService teacherContentAccessService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApprovedTeacherInterceptor(TeacherContentAccessService teacherContentAccessService) {
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isOpenPath(path)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityUserDetails currentUser = authentication != null
                && authentication.getPrincipal() instanceof SecurityUserDetails details ? details : null;
        if (LEARNER_FEATURE_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            teacherContentAccessService.requireApprovedTeacherAccess(currentUser);
        } else {
            // Content, activities, quizzes, live classes and videos keep the content-authoring message.
            teacherContentAccessService.requireApprovedTeacherForContent(currentUser);
        }
        return true;
    }

    boolean isOpenPath(String path) {
        return OPEN_TEACHER_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}