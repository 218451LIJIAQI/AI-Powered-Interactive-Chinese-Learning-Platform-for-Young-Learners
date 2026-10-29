package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.aichinese.service.TeacherContentAccessService;

/** FRTVER04 / SDD T03: only the teacher's profile and the verification workflow stay open to unapproved teachers. */
class ApprovedTeacherInterceptorTests {

    private final ApprovedTeacherInterceptor interceptor =
            new ApprovedTeacherInterceptor(mock(TeacherContentAccessService.class));

    @Test
    void profileAndVerificationAreOpen() {
        assertThat(interceptor.isOpenPath("/api/v1/teachers/me")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/me/profile")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification/submit")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification-documents")).isTrue();
        assertThat(interceptor.isOpenPath("/api/v1/teachers/verification-documents/12")).isTrue();
    }

    @Test
    void everyOtherTeacherFeatureIsGuarded() {
        for (String path : new String[] {
                "/api/v1/teachers/teaching-dashboard",
                "/api/v1/teachers/students",
                "/api/v1/teachers/students/9",
                "/api/v1/teachers/students/9/learning-goal",
                "/api/v1/teachers/feedback",
                "/api/v1/teachers/feedback/4",
                "/api/v1/teachers/classes",
                "/api/v1/teachers/classes/2/units",
                "/api/v1/teachers/lessons/3",
                "/api/v1/teachers/lessons/3/quizzes",
                "/api/v1/teachers/live-sessions",
                "/api/v1/teachers/ai/quiz-draft",
                "/api/v1/teachers/me/../students" }) {
            assertThat(interceptor.isOpenPath(path)).as(path).isFalse();
        }
    }
}
