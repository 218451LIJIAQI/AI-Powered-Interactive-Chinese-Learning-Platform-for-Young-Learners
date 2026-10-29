package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.example.aichinese.support.ApiIntegrationTest;

/**
 * End-to-end MockMvc coverage of the persona home pages (student Home, admin dashboard): each one's authorization
 * scope and the single-fetch response the page depends on.
 */
class DashboardControllerIntegrationTests extends ApiIntegrationTest {

    private static final String TEACHER_STATUS_PENDING = "PENDING";

    @Test
    void studentGetsOwnHome() throws Exception {
        String token = api.registerStudent(uniqueEmail("student-dashboard"));

        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").isString())
                .andExpect(jsonPath("$.data.today.dailyGoalXp").isNumber())
                .andExpect(jsonPath("$.data.setupState").value("NOT_STARTED"));
    }

    @Test
    void adminGetsAdminDashboardSummary() throws Exception {
        String token = api.adminToken();

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.numbers.users.students").isNumber())
                .andExpect(jsonPath("$.data.numbers.users.teachers").isNumber())
                .andExpect(jsonPath("$.data.numbers.aiRequests").isNumber())
                .andExpect(jsonPath("$.data.teacherApplications.count").isNumber())
                .andExpect(jsonPath("$.data.reviewQueue.count").isNumber())
                .andExpect(jsonPath("$.data.aiReports.count").isNumber())
                .andExpect(jsonPath("$.data.support.count").isNumber())
                .andExpect(jsonPath("$.data.registrations.length()").value(7))
                .andExpect(jsonPath("$.data.paymentRevenueSummary").doesNotExist())
                .andExpect(jsonPath("$.data.pendingRefunds").doesNotExist())
                .andExpect(jsonPath("$.data.pendingPayouts").doesNotExist());
    }

    @Test
    void shouldRejectUnauthenticatedDashboardRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectStudentAccessToTeacherDashboard() throws Exception {
        String token = api.registerStudent(uniqueEmail("student-teacher-dash"));

        mockMvc.perform(api.authGet("/api/v1/teachers/dashboard", token))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectTeacherAccessToAdminDashboard() throws Exception {
        String token = api.registerTeacher(uniqueEmail("teacher-for-admin-dashboard"));

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));
    }
}
