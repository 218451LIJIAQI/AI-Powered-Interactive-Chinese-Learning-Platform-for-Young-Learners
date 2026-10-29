package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * MockMvc coverage of the admin analytics: the chosen period, counts by status and role, and the role checks
 * (anonymous and wrong-role requests are rejected).
 */
class AnalyticsIntegrationTests extends ApiIntegrationTest {

    private static final String ADMIN_ANALYTICS_PATH = "/api/v1/admin/analytics";

    @Test
    void adminAnalyticsCoversTheChosenPeriodAndEveryStatus() throws Exception {
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH + "?days=30", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.registrations.length()").value(30))
                .andExpect(jsonPath("$.data.contentByStatus.PUBLISHED").isNumber())
                .andExpect(jsonPath("$.data.contentByStatus.DRAFT").isNumber())
                .andExpect(jsonPath("$.data.aiRequestsByStatus.SUCCESS").isNumber())
                .andExpect(jsonPath("$.data.learning.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.overview").doesNotExist());
        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH + "?days=12", adminToken))
                .andExpect(jsonPath("$.data.days").value(7));
    }

    @Test
    void adminAnalyticsCountsNewAccountsByRoleAndDay() throws Exception {
        String adminToken = api.adminToken();
        JsonNode baseline = analyticsData(adminToken);

        api.registerTeacher(uniqueEmail("teacher-admin-aggregate"));
        api.registerStudent(uniqueEmail("student-admin-aggregate"));
        JsonNode after = analyticsData(adminToken);

        assertThat(after.path("numbers").path("users").path("teachers").asLong())
                .isEqualTo(baseline.path("numbers").path("users").path("teachers").asLong() + 1L);
        assertThat(after.path("numbers").path("users").path("students").asLong())
                .isEqualTo(baseline.path("numbers").path("users").path("students").asLong() + 1L);
        assertThat(after.path("newRegistrations").asLong()).isEqualTo(baseline.path("newRegistrations").asLong() + 2L);
        JsonNode today = after.path("registrations").get(after.path("registrations").size() - 1);
        assertThat(today.path("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(today.path("count").asLong()).isPositive();
    }

    @Test
    void analyticsEndpointsShouldRejectUnauthorizedRequests() throws Exception {
        assertUnauthorized(ADMIN_ANALYTICS_PATH);
    }

    @Test
    void analyticsEndpointsShouldRejectWrongRoles() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("student-wrong-role"));
        String teacherToken = api.approvedTeacher(uniqueEmail("teacher-wrong-role"));

        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH, studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH, teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private JsonNode analyticsData(String adminToken) throws Exception {
        return api.data(mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH, adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void assertUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
