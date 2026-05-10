package com.example.aichinese;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studentAnalyticsShouldReturnOwnedSummary() throws Exception {
        String studentToken = registerAndLoginStudent(uniqueEmail("student-analytics"));

        mockMvc.perform(get("/api/v1/students/analytics")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.completedLessons").isNumber())
                .andExpect(jsonPath("$.data.overview.averageQuizScore").exists())
                .andExpect(jsonPath("$.data.quizScoreTrend").isArray())
                .andExpect(jsonPath("$.data.lessonStatusBreakdown").isArray());
    }

    @Test
    void teacherAnalyticsShouldReturnOwnedSummary() throws Exception {
        String teacherToken = registerAndLoginTeacher(uniqueEmail("teacher-analytics"));

        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.totalLessons").isNumber())
                .andExpect(jsonPath("$.data.overview.netEarnings").exists())
                .andExpect(jsonPath("$.data.contentStatusBreakdown").isArray())
                .andExpect(jsonPath("$.data.reviewRatingBreakdown").isArray());
    }

    @Test
    void adminAnalyticsShouldReturnPlatformSummary() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        mockMvc.perform(get("/api/v1/admin/analytics")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.overview.totalTeachers").isNumber())
                .andExpect(jsonPath("$.data.userRegistrationTrend").isArray())
                .andExpect(jsonPath("$.data.teacherApprovalBreakdown").isArray())
                .andExpect(jsonPath("$.data.aiStatusBreakdown").isArray());
    }

    @Test
    void analyticsEndpointsShouldRejectUnauthorizedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/students/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/teachers/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/admin/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void analyticsEndpointsShouldRejectWrongRoles() throws Exception {
        String studentToken = registerAndLoginStudent(uniqueEmail("student-wrong-role-analytics"));
        String teacherToken = registerAndLoginTeacher(uniqueEmail("teacher-wrong-role-analytics"));

        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/admin/analytics")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String registerAndLoginStudent(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Analytics Tester",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private String registerAndLoginTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher Analytics Tester",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher analytics test user"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix.replace("-", "") + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }
}
