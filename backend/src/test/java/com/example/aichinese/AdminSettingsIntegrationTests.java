package com.example.aichinese;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MockMvc coverage of {@code /api/v1/admin/system-settings/platform}
 * (AdminSettingsController): admins can read and update the platform
 * settings; other roles and anonymous callers are rejected, and the removed
 * commerce settings endpoint no longer exists.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSettingsIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String PLATFORM_SETTINGS_PATH = "/api/v1/admin/system-settings/platform";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanManagePlatformSettings() throws Exception {
        String adminToken = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        String studentToken = registerStudentAndLogin(uniqueEmail("platform-settings-student"));

        mockMvc.perform(put(PLATFORM_SETTINGS_PATH)
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "aiDisclaimerText", "Teacher guidance should still be reviewed.",
                        "sensitiveWordFilter", true,
                        "aiSafetyCheck", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiDisclaimerText").value("Teacher guidance should still be reviewed."))
                .andExpect(jsonPath("$.data.sensitiveWordFilter").value(true))
                .andExpect(jsonPath("$.data.aiSafetyCheck").value(false))
                .andExpect(jsonPath("$.data.emailNotifications").doesNotExist())
                .andExpect(jsonPath("$.data.inAppNotifications").doesNotExist())
                .andExpect(jsonPath("$.data.dailySummary").doesNotExist())
                .andExpect(jsonPath("$.data.strictContentFiltering").doesNotExist());

        mockMvc.perform(get(PLATFORM_SETTINGS_PATH)
                .header(AUTHORIZATION_HEADER, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformName")
                        .value("AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia"))
                .andExpect(jsonPath("$.data.aiDisclaimerText").value("Teacher guidance should still be reviewed."))
                .andExpect(jsonPath("$.data.aiSafetyCheck").value(false));

        mockMvc.perform(get(PLATFORM_SETTINGS_PATH)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isForbidden());

        // Restore the defaults so other tests sharing this context see the enforced AI safety check.
        mockMvc.perform(put(PLATFORM_SETTINGS_PATH)
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "aiDisclaimerText", "AI responses may contain errors and do not replace your teacher.",
                        "sensitiveWordFilter", true,
                        "aiSafetyCheck", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSafetyCheck").value(true));
    }

    @Test
    void platformSettingsRejectAnonymousCallersAndCommerceSettingsAreGone() throws Exception {
        mockMvc.perform(get(PLATFORM_SETTINGS_PATH))
                .andExpect(status().isUnauthorized());

        String adminToken = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/admin/system-settings/commission")
                .header(AUTHORIZATION_HEADER, bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/payments")
                .header(AUTHORIZATION_HEADER, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
