package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The admin audit log (search, filters, one entry, actions in use, CSV export) and the public health check. */
class AdminAuditLogIntegrationTests extends ApiIntegrationTest {

    private static final String AUDIT = "/api/v1/admin/audit-logs";

    @Autowired
    private LearnClock learnClock;

    @Test
    void auditLogFindsAnAdminDecisionByKeywordActionActorAndDate() throws Exception {
        String email = uniqueEmail("audit-target");
        api.registerStudentAccount(email);
        Long userId = user(email).getId();
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + userId + "/deactivate", adminToken,
                        Map.of("reason", "Shared the account with another person.")))
                .andExpect(status().isOk());
        LocalDate today = learnClock.today();

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
        mockMvc.perform(api.authGet(AUDIT + "?from=" + today + "&to=" + today.minusDays(1), adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The from date must be on or before the to date."));
    }

    /**
     * From / to are platform days (LearnClock), like "today" everywhere else, whatever zone the server runs in: the
     * first and the last minute of a platform day are both on it, though one of them falls on another server-local day
     * whenever the server's zone is not the platform's.
     */
    @Test
    void dateFiltersArePlatformDays() throws Exception {
        String action = "TEST_PLATFORM_DAY_" + uniqueSuffix().toUpperCase(Locale.ROOT);
        LocalDate day = learnClock.today().minusDays(400);
        for (LocalDateTime at : List.of(learnClock.startOf(day).plusMinutes(1),
                learnClock.startOf(day.plusDays(1)).minusMinutes(1))) {
            AuditLog entry = new AuditLog();
            entry.setActionType(action);
            entry.setDescription("Platform day boundary.");
            entry.setCreatedAt(at);
            entry.setUpdatedAt(at);
            auditLogRepository.save(entry);
        }
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(AUDIT + "?actionType=" + action + "&from=" + day + "&to=" + day, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2));
        mockMvc.perform(api.authGet(AUDIT + "?actionType=" + action + "&to=" + day.minusDays(1), adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(AUDIT + "?actionType=" + action + "&from=" + day.plusDays(1), adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    /** Sign-out and password rows are about the account too: they name it and open it, like the admin's rows above. */
    @Test
    void anAccountsOwnActionsNameAndLinkTheAccount() throws Exception {
        String email = uniqueEmail("audit_own-action");
        String studentToken = api.registerStudent(email);
        Long userId = user(email).getId();
        mockMvc.perform(api.authPost("/api/v1/auth/logout", studentToken)).andExpect(status().isOk());
        String otherToken = api.registerStudent(email.replace('_', '-'));
        mockMvc.perform(api.authPost("/api/v1/auth/logout", otherToken)).andExpect(status().isOk());
        String adminToken = api.adminToken();

        for (String filter : List.of("actor", "keyword")) {
            mockMvc.perform(api.authGet(AUDIT + "?actionType=LOGOUT&" + filter + "=" + email, adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalItems").value(1))
                    .andExpect(jsonPath("$.data.items[0].targetLabel").value("Student " + email))
                    .andExpect(jsonPath("$.data.items[0].targetUserId").value(userId));
        }
    }

    /**
     * The Users page's "View all": the entries by or about one account, by id. A search by the account's current name
     * would miss entries without a label or written under an earlier name, and find accounts whose names contain it.
     */
    @Test
    void theUserFilterListsExactlyTheEntriesByOrAboutOneAccountAndTheExportFollowsIt() throws Exception {
        String suffix = uniqueSuffix();
        String email = uniqueEmail("audit-user-filter");
        String learnerToken = api.registerStudent(email);
        Long userId = user(email).getId();
        rename(userId, "Old name " + suffix);
        mockMvc.perform(api.authPost("/api/v1/auth/logout", learnerToken)).andExpect(status().isOk());
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + userId + "/deactivate", adminToken,
                        Map.of("reason", "Asked to pause learning.")))
                .andExpect(status().isOk());
        rename(userId, "Mira " + suffix);
        AuditLog unlabelled = new AuditLog();
        unlabelled.setActionType("EMAIL_CONFIRMED");
        unlabelled.setTargetType("User");
        unlabelled.setTargetId(userId);
        unlabelled.setDescription("User confirmed the email address.");
        unlabelled = auditLogRepository.save(unlabelled);
        // Another account whose name contains this one's, with entries of its own.
        String otherEmail = uniqueEmail("audit-user-filter-other");
        api.registerStudentAccount(otherEmail);
        Long otherId = user(otherEmail).getId();
        rename(otherId, "Mira " + suffix + " Lee");
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + otherId + "/deactivate", adminToken,
                        Map.of("reason", "Another account.")))
                .andExpect(status().isOk());

        List<AuditLog> all = auditLogRepository.findAll();
        List<String> expected = all.stream()
                .filter(entry -> (entry.getActorUser() != null && userId.equals(entry.getActorUser().getId()))
                        || ("User".equals(entry.getTargetType()) && userId.equals(entry.getTargetId())))
                .map(entry -> entry.getId().toString())
                .toList();
        List<String> otherAccounts = all.stream()
                .filter(entry -> (entry.getActorUser() != null && otherId.equals(entry.getActorUser().getId()))
                        || ("User".equals(entry.getTargetType()) && otherId.equals(entry.getTargetId())))
                .map(entry -> entry.getId().toString())
                .toList();
        assertThat(otherAccounts).isNotEmpty();

        JsonNode items = api.data(mockMvc.perform(api.authGet(AUDIT + "?userId=" + userId + "&size=50", adminToken))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        List<String> listed = items.findValuesAsText("id");
        assertThat(listed).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(items.findValuesAsText("actionType")).contains(
                "ACCOUNT_CREATED", "LOGOUT", "ADMIN_USER_DEACTIVATE", "EMAIL_CONFIRMED");
        assertThat(listed).contains(unlabelled.getId().toString()).doesNotContainAnyElementsOf(otherAccounts);
        // The newest first; the entry without a label is named after the account as it is now.
        JsonNode newest = items.get(0);
        assertThat(newest.path("id").asLong()).isEqualTo(unlabelled.getId());
        assertThat(newest.path("targetLabel").asText()).isEqualTo("Mira " + suffix);
        // With the other filters too; the decision keeps the name the account had then.
        mockMvc.perform(api.authGet(AUDIT + "?userId=" + userId + "&actionType=ADMIN_USER_DEACTIVATE", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].targetLabel").value("Old name " + suffix))
                .andExpect(jsonPath("$.data.items[0].reason").value("Asked to pause learning."));
        // The user drawer's recent entries are the account's own events, newest first.
        JsonNode recent = api.data(mockMvc.perform(api.authGet("/api/v1/admin/users/" + userId, adminToken))
                .andExpect(status().isOk())
                .andReturn()).path("recentAudit");
        assertThat(recent.get(0).path("id").asLong()).isEqualTo(unlabelled.getId());
        assertThat(recent.findValuesAsText("actionType"))
                .containsSubsequence("EMAIL_CONFIRMED", "ADMIN_USER_DEACTIVATE", "LOGOUT", "ACCOUNT_CREATED");

        String csv = mockMvc.perform(api.authGet(AUDIT + "/export?userId=" + userId, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.split("\r\n")).hasSize(expected.size() + 1);
        assertThat(csv).contains(",Old name " + suffix + ",Asked to pause learning.,",
                        ",Mira " + suffix + ",,User confirmed the email address.")
                .doesNotContain("Another account.");
    }

    private void rename(Long userId, String name) {
        User account = userRepository.findById(userId).orElseThrow();
        account.setDisplayName(name);
        userRepository.save(account);
    }

    @Test
    void auditLogExportsTheFilteredEntriesAsCsv() throws Exception {
        String adminToken = api.adminToken();
        String email = uniqueEmail("audit-export");
        api.registerStudentAccount(email);
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + user(email).getId() + "/deactivate", adminToken,
                        Map.of("reason", "=1+1")))
                .andExpect(status().isOk());

        MvcResult exportResult = mockMvc.perform(api.authGet(
                        AUDIT + "/export?actionType=ADMIN_USER_DEACTIVATE&keyword=" + email, adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(content().string(startsWith("\uFEFFTime,Action,Category,Actor,Actor email,Target,Reason,Description")))
                // A reason a spreadsheet would run as a formula is written as plain text.
                .andExpect(content().string(containsString(",'=1+1,")))
                .andReturn();
        // The download of names and addresses leaves its own trace tied to this request id and actor.
        assertThat(auditForRequest(exportResult.getResponse().getHeader("X-Request-Id"), "AUDIT_LOG_EXPORTED"))
                .hasValueSatisfying(entry -> assertThat(entry.getActorEmail()).isEqualTo(ADMIN_EMAIL));
    }

    @Test
    void onlyAdminsReadTheAuditLog() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("audit-student"));

        mockMvc.perform(api.authGet(AUDIT, studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(AUDIT + "/export", studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(AUDIT)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckReportsTheServerWithItsDatabaseAndTheAiProvider() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.ai").value("UNAVAILABLE"));
    }
}
