package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.aichinese.service.TeacherContentAccessService;

/** D13: only the dashboard shell, profile and verification stay open to unapproved teachers. */
class ApprovedTeacherInterceptorTests {

    private final ApprovedTeacherInterceptor interceptor =
            new ApprovedTeacherInterceptor(mock(TeacherContentAccessService.class));

    @Test
    void dashboardShellProfileAndVerificationAreOpen() {
        assertThat(interceptor.isOpenPath("/api/v1/teachers/me")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/me/profile")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/dashboard")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification-status")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification-documents")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification-documents/12")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/resubmit-verification")).isTrue();
    }

    @Test
    void everyOtherTeacherFeatureIsGuarded() {
        for (String path : new String[] {
                "/api/v1/teachers/students",
                "/api/v1/teachers/feedback",
                "/api/v1/teachers/feedback/4",
                "/api/v1/teachers/analytics",
                "/api/v1/teachers/modules",
                "/api/v1/teachers/lessons/3/quizzes",
                "/api/v1/teachers/live-classes",
                "/api/v1/teachers/lessons/3/recorded-videos",
                "/api/v1/teachers/activities/flashcards",
                "/api/v1/teachers/ai/quiz-draft",
                "/api/v1/teachers/me/../students" }) {
            assertThat(interceptor.isOpenPath(path)).as(path).isFalse();
        }
    }
}
