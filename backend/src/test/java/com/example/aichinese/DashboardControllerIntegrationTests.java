package com.example.aichinese;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end MockMvc coverage of the three persona dashboards
 * (student / teacher / admin) - pins each dashboard's authorization
 * scope and the fixed-shape single-fetch response contract the
 * front-end home page depends on.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String NO_STORE = "no-store";
    private static final String TEACHER_STATUS_PENDING = "PENDING";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studentGetsOwnDashboardSummary() throws Exception {
        String token = registerAndLoginStudent(uniqueEmail("student-dashboard"));

        mockMvc.perform(get("/api/v1/students/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").isString())
                .andExpect(jsonPath("$.data.currentLevel").isString())
                .andExpect(jsonPath("$.data.learningProgressPercent").isNumber());
    }

    @Test
    void pendingTeacherGetsTeacherDashboardSummary() throws Exception {
        String token = registerAndLoginTeacher(uniqueEmail("teacher-dashboard"));

        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.teacherStatus").value(TEACHER_STATUS_PENDING))
                .andExpect(jsonPath("$.data.totalLessons").isNumber())
                .andExpect(jsonPath("$.data.averageQuizScore").isNumber())
                .andExpect(jsonPath("$.data.earningsSummary").doesNotExist());
    }

    @Test
    void adminGetsAdminDashboardSummary() throws Exception {
        String token = userToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.totalTeachers").isNumber())
                .andExpect(jsonPath("$.data.aiUsageSummary.totalRequests").isNumber())
                .andExpect(jsonPath("$.data.paymentRevenueSummary").doesNotExist())
                .andExpect(jsonPath("$.data.pendingRefunds").doesNotExist())
                .andExpect(jsonPath("$.data.pendingPayouts").doesNotExist());
    }

    @Test
    void shouldRejectUnauthenticatedDashboardRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectStudentAccessToTeacherDashboard() throws Exception {
        String token = registerAndLoginStudent(uniqueEmail("student-for-teacher-dashboard"));

        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectTeacherAccessToAdminDashboard() throws Exception {
        String token = registerAndLoginTeacher(uniqueEmail("teacher-for-admin-dashboard"));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(noStoreCacheControl())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String registerAndLoginStudent(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", Map.of(
                        "fullName", "Student Dashboard Tester",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());

        return userToken(email, USER_PASSWORD);
    }

    private String registerAndLoginTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", Map.of(
                        "fullName", "Teacher Dashboard Tester",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Teacher dashboard test user")))
                .andExpect(status().isOk());

        return userToken(email, USER_PASSWORD);
    }

    private String userToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/login", Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Map<String, ?> payload) throws Exception {
        return post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static ResultMatcher noStoreCacheControl() {
        return header().string(HttpHeaders.CACHE_CONTROL, containsString(NO_STORE));
    }

    private String uniqueEmail(String prefix) {
        return prefix.replace("-", "") + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }
}
