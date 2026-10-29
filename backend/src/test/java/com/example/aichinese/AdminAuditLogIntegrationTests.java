package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The admin audit log (search, filters, one entry, actions in use, CSV export) and the public health check. */
class AdminAuditLogIntegrationTests extends ApiIntegrationTest {

    private static final String AUDIT = "/api/v1/admin/audit-logs";

    @Test
    void auditLogFindsAnAdminDecisionByKeywordActionActorAndDate() throws Exception {
        String email = uniqueEmail("audit-target");
        api.registerStudent(email);
        Long userId = user(email).getId();
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + userId + "/deactivate", adminToken,
                        Map.of("reason", "Shared the account with another person.")))
                .andExpect(status().isOk());
        String today = LocalDate.now().toString();

        JsonNode page = api.data(mockMvc.perform(api.authGet(AUDIT + "?actionType=ADMIN_USER_DEACTIVATE&actor="
                                + ADMIN_EMAIL + "&from=" + today + "&to=" + today + "&keyword=Student", adminToken))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode entry = page.path("items").get(0);
        assertThat(entry.path("actionType").asText()).isEqualTo("ADMIN_USER_DEACTIVATE");
        assertThat(entry.path("category").asText()).isEqualTo("ACCOUNT");
        assertThat(entry.path("targetLabel").asText()).isEqualTo("Student " + email);
        assertThat(entry.path("targetUserId").asLong()).isEqualTo(userId);
        assertThat(entry.path("reason").asText()).isEqualTo("Shared the account with another person.");

        mockMvc.perform(api.authGet(AUDIT + "/" + entry.path("id").asLong(), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorEmail").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.actorName").exists());
        mockMvc.perform(api.authGet(AUDIT + "/actions", adminToken))
                .andExpect(jsonPath("$.data", hasItem("ADMIN_USER_DEACTIVATE")));
        mockMvc.perform(api.authGet(AUDIT + "?from=" + today + "&to=" + LocalDate.now().minusDays(1), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The from date must be on or before the to date."));
    }

    @Test
    void auditLogExportsTheFilteredEntriesAsCsv() throws Exception {
        String adminToken = api.adminToken();
        api.registerStudent(uniqueEmail("audit-export"));

        mockMvc.perform(api.authGet(AUDIT + "/export?keyword=", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(content().string(startsWith("\uFEFFTime,Action,Category,Actor,Actor email,Target,Reason,Description")));
    }

    @Test
    void onlyAdminsReadTheAuditLog() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("audit-student"));

        mockMvc.perform(api.authGet(AUDIT, studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(AUDIT + "/export", studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(AUDIT)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckReportsTheServerTheDatabaseAndTheAiProvider() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.ai").value(containsString("AVAILABLE")));
    }
}
