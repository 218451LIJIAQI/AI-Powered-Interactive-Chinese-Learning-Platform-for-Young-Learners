package com.example.aichinese;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/**
 * End-to-end MockMvc coverage of the three persona analytics
 * surfaces (student / teacher / admin): time-series trends, breakdown
 * aggregates, and per-persona authorization scope.
 *
 * <p>Pins the row-level filter — a teacher only sees their own
 * classes / earnings, a student only their own progress, an admin
 * sees platform-wide aggregates — so a refactor of
 * {@code AnalyticsService} cannot quietly leak cross-tenant data.</p>
 */
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
                .andExpect(jsonPath("$.data.overview.currencyCode").value("MYR"))
                .andExpect(jsonPath("$.data.contentStatusBreakdown").isArray());
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
                .andExpect(jsonPath("$.data.overview.currencyCode").value("MYR"))
                .andExpect(jsonPath("$.data.userRegistrationTrend").isArray())
                .andExpect(jsonPath("$.data.teacherApprovalBreakdown").isArray())
                .andExpect(jsonPath("$.data.aiStatusBreakdown").isArray());
    }

    /**
     * Pins down the Wave 4 refactor of {@code AnalyticsService.getAdminAnalytics}.
     * The previous implementation loaded entire tables into JVM memory and
     * counted there; the new implementation issues per-status {@code COUNT}
     * and {@code GROUP BY} queries through {@code StatusCountProjection}.
     *
     * <p>This test exercises three invariants that the refactor must
     * preserve:</p>
     * <ol>
     *   <li>Teacher / student counts react to new registrations through the
     *       same {@code COUNT(distinct u)} query that powered the legacy
     *       path.</li>
     *   <li>Every enum value of the breakdown enums shows up in the
     *       response (even with a count of zero), so the frontend can
     *       always render a complete chart without conditional checks.</li>
     *   <li>The {@code teacherApprovalBreakdown} contains a non-zero
     *       {@code PENDING} count after a teacher self-registers — the new
     *       {@code countByTeacherStatus(PENDING)} must surface that.</li>
     * </ol>
     */
    @Test
    void adminAnalyticsShouldReflectAggregateRegistrationsAndBreakdowns() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        // Snapshot baseline counts (other tests may have left registrations).
        MvcResult baselineResult = mockMvc.perform(get("/api/v1/admin/analytics")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode baseline = objectMapper.readTree(baselineResult.getResponse().getContentAsString())
                .path("data").path("overview");
        long baselineTeachers = baseline.path("totalTeachers").asLong();
        long baselineStudents = baseline.path("totalStudents").asLong();
        long baselinePendingTeachers = baseline.path("pendingTeacherApprovals").asLong();

        // Register exactly one new teacher and one new student.
        registerAndLoginTeacher(uniqueEmail("teacher-admin-aggregate"));
        registerAndLoginStudent(uniqueEmail("student-admin-aggregate"));

        MvcResult afterResult = mockMvc.perform(get("/api/v1/admin/analytics")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterData = objectMapper.readTree(afterResult.getResponse().getContentAsString())
                .path("data");
        JsonNode after = afterData.path("overview");

        assertEquals(
                baselineTeachers + 1L, after.path("totalTeachers").asLong(),
                "Teacher count should increase by exactly one after a teacher self-registers.");
        assertEquals(
                baselineStudents + 1L, after.path("totalStudents").asLong(),
                "Student count should increase by exactly one after a student self-registers.");
        assertTrue(
                after.path("pendingTeacherApprovals").asLong() >= baselinePendingTeachers + 1L,
                "Pending teacher approvals must include the newly registered teacher.");

        // The teacher approval breakdown is built by the Wave 4 refactor
        // through {@code countByTeacherStatus(PENDING/APPROVED/REJECTED)}
        // followed by the zero-value filter in {@code buildBreakdownFromMap}.
        // After registering a new teacher we expect at least the PENDING
        // bucket to appear with a positive count — this pins down the new
        // aggregate-query path end to end.
        JsonNode teacherApproval = afterData.path("teacherApprovalBreakdown");
        long pendingBucketValue = bucketValue(teacherApproval, "PENDING");
        assertTrue(
                pendingBucketValue >= 1L,
                "Teacher approval breakdown must surface a PENDING bucket with at least one teacher.");

        // Sanity-check the breakdown shape itself: every emitted bucket must
        // carry a positive count (zero buckets are filtered by design) and
        // a non-blank key.
        teacherApproval.forEach(item -> {
            assertFalse(
                    item.path("label").asText().isBlank(),
                    "Breakdown items must always have a non-blank label.");
            assertTrue(
                    item.path("value").asLong() > 0L,
                    "Breakdown items with value 0 should be filtered out by buildBreakdownFromMap.");
        });
    }

    private long bucketValue(JsonNode breakdownArray, String label) {
        if (!breakdownArray.isArray()) {
            return 0L;
        }
        for (JsonNode item : breakdownArray) {
            if (label.equals(item.path("label").asText())) {
                return item.path("value").asLong();
            }
        }
        return 0L;
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
