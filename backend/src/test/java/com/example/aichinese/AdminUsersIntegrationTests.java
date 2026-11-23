package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withEarlierRead;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.nextClientIp;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.admin.AdminInviteService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The admin Users section: learners and admins, user search and detail with the account's own recent events,
 * deactivation with its reason, reactivation, and admin invites.
 */
class AdminUsersIntegrationTests extends ApiIntegrationTest {

    private static final String USERS = "/api/v1/admin/users";
    private static final String INVITES = "/api/v1/admin/admin-invites";
    private static final Map<String, Object> REASON = Map.of("reason", "Asked by email to pause learning.");

    @Autowired
    private AdminInviteRepository adminInviteRepository;

    @Autowired
    private AdminInviteService adminInviteService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void userListSearchesAndFiltersLearnersAndAdmins() throws Exception {
        String marker = "users" + uniqueSuffix();
        for (int index = 0; index < 12; index++) {
            api.registerStudentAccount(marker + "-" + index + "@example.com");
        }
        String newest = marker + "_0@example.com";
        api.registerStudentAccount(newest);
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&size=5", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(13))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].email").value(newest))
                .andExpect(jsonPath("$.data.items[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.items[0].verificationState").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "_0", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].email").value(newest));
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&role=STUDENT", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(13));
        mockMvc.perform(api.authGet(USERS, adminToken).param("keyword", marker).param("role", "STUDENT")
                        .param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(13));
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&role=ADMIN", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(USERS + "?keyword=" + ADMIN_EMAIL + "&role=ADMIN", adminToken))
                .andExpect(jsonPath("$.data.items[0].role").value("ADMIN"));
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&active=false", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        for (String role : List.of("NOBODY", "TEACHER")) {
            mockMvc.perform(api.authGet(USERS + "?role=" + role, adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid user role filter."));
        }
    }

    @Test
    void deactivationSignsOutBlocksSignInAndIsAuditedWithItsReason() throws Exception {
        String email = uniqueEmail("users-deactivate");
        String studentToken = api.registerStudent(email);
        Long userId = user(email).getId();
        String adminToken = api.adminToken();
        String userPath = USERS + "/" + userId;
        String deactivatePath = userPath + "/deactivate";

        mockMvc.perform(api.authGet(userPath, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.self").value(false))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.recentAudit[0].actionType").value("ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.data.deactivationImpact").doesNotExist());
        mockMvc.perform(api.jsonPost(deactivatePath, adminToken, Map.of("reason", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(deactivatePath, studentToken, REASON))
                .andExpect(status().isForbidden());

        mockMvc.perform(api.jsonPost(deactivatePath, adminToken, REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.deactivation.reason").value(REASON.get("reason")))
                .andExpect(jsonPath("$.data.recentAudit[0].actorEmail").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.recentAudit[0].actionType").value("ADMIN_USER_DEACTIVATE"))
                .andExpect(jsonPath("$.data.cancelledSessions").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/auth/me", studentToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "This account is currently unavailable. Please contact the platform administrator."));
        // A retried request changes nothing and is not audited a second time.
        mockMvc.perform(api.jsonPost(deactivatePath, adminToken, REASON))
                .andExpect(status().isOk());
        assertThat(auditEntriesAbout("User", userId))
                .filteredOn(entry -> "ADMIN_USER_DEACTIVATE".equals(entry.getActionType()))
                .hasSize(1);

        mockMvc.perform(api.jsonPost(userPath + "/reactivate", adminToken,
                        Map.of("reason", "Spoke with the learner.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(api.login(email, USER_PASSWORD)).isNotBlank();
        assertThat(auditEntriesAbout("User", userId)).anyMatch(entry ->
                "ADMIN_USER_REACTIVATE".equals(entry.getActionType())
                        && "Spoke with the learner.".equals(entry.getReason()));
        assertThat(notificationsOf(email))
                .anyMatch(notification -> "Your account is active again".equals(notification.getTitle()));
    }

    /** Reactivating runs at once (UI rules: what is easy to take back needs no question): no reason, no body needed. */
    @Test
    void reactivationNeedsNoReason() throws Exception {
        String email = uniqueEmail("users-reactivate");
        api.registerStudentAccount(email);
        Long userId = user(email).getId();
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, REASON))
                .andExpect(status().isOk());

        mockMvc.perform(api.authPost(USERS + "/" + userId + "/reactivate", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.deactivation").doesNotExist());
        assertThat(api.login(email, USER_PASSWORD)).isNotBlank();
        assertThat(auditEntriesAbout("User", userId))
                .filteredOn(entry -> "ADMIN_USER_REACTIVATE".equals(entry.getActionType()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getReason()).isNull());

        // A blank reason is none; deactivating still needs one.
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, Map.of("reason", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, REASON))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/reactivate", adminToken, Map.of("reason", "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(auditEntriesAbout("User", userId))
                .filteredOn(entry -> "ADMIN_USER_REACTIVATE".equals(entry.getActionType()))
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getReason()).isNull());
    }

    /**
     * A teacher account lost its role when teachers went (V3 deactivated it and kept the row for its history): it is no
     * longer listed, its detail shows no role, and it cannot be reactivated.
     */
    @Test
    void anAccountWithoutARoleIsNotListedAndCannotBeReactivated() throws Exception {
        String email = uniqueEmail("users-former-teacher");
        api.registerStudentAccount(email);
        Long userId = user(email).getId();
        jdbc.update("delete from user_roles where user_id = ?", userId);
        jdbc.update("update users set active = false where id = ?", userId);
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(USERS + "?keyword=" + email, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(USERS + "/" + userId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/reactivate", adminToken, Map.of("reason", "Come back.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This account has no role any more, so it can't be reactivated."));
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDeactivateTheirOwnAccount() throws Exception {
        String adminToken = api.adminToken();
        Long adminId = user(ADMIN_EMAIL).getId();

        mockMvc.perform(api.authGet(USERS + "/" + adminId, adminToken))
                .andExpect(jsonPath("$.data.self").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        mockMvc.perform(api.jsonPost(USERS + "/" + adminId + "/deactivate", adminToken, REASON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own admin account."));
        mockMvc.perform(api.jsonPost(USERS + "/999999/deactivate", adminToken, REASON))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitedAdminSetsAPasswordAndAppearsAsAnActiveAdmin() throws Exception {
        String email = uniqueEmail("users-invite");
        String adminToken = api.adminToken();

        long inviteId = sendInvite(adminToken, email).path("id").asLong();
        mockMvc.perform(api.authGet(INVITES, adminToken))
                .andExpect(jsonPath("$.data[*].email", hasItem(email)));
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();
        mockMvc.perform(api.jsonPost(INVITES, adminToken, Map.of("email", ADMIN_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));

        String rawToken = knownInviteToken(inviteId);
        mockMvc.perform(inviteLink("check", Map.of("token", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(inviteLink("accept", Map.of(
                        "token", rawToken, "fullName", "Farid Admin",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isOk());

        String newAdminToken = api.login(email, USER_PASSWORD);
        mockMvc.perform(api.authGet("/api/v1/auth/me", newAdminToken))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.fullName").value("Farid Admin"))
                .andExpect(jsonPath("$.data.teacherProfile").doesNotExist());
        mockMvc.perform(api.authGet(INVITES, adminToken))
                .andExpect(jsonPath("$.data[*].email", not(hasItem(email))));
        mockMvc.perform(inviteLink("accept", Map.of(
                        "token", rawToken, "fullName", "Again",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    /** "Cancel invite" (A2.04): the invite leaves the pending list and its link stops working; it is audited. */
    @Test
    void aCancelledInviteLinkNoLongerWorks() throws Exception {
        String email = uniqueEmail("users-invite-cancel");
        String adminToken = api.adminToken();
        long inviteId = sendInvite(adminToken, email).path("id").asLong();
        String rawToken = knownInviteToken(inviteId);

        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invite cancelled. The link no longer works."));
        mockMvc.perform(api.authGet(INVITES, adminToken))
                .andExpect(jsonPath("$.data[*].email", not(hasItem(email))));
        mockMvc.perform(inviteLink("check", Map.of("token", rawToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId, adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This invite was not found."));
        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId,
                        api.registerStudent(uniqueEmail("users-invite-cancel-student"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(inviteLink("accept", Map.of(
                        "token", rawToken, "fullName", "Too Late",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();
    }

    /**
     * The Undo of "Cancel invite": the invite is pending again as it was listed (its sending time unchanged) and the link
     * already sent works again. Both steps are audited.
     */
    @Test
    void anUndoneCancelBringsTheSameLinkBack() throws Exception {
        String email = uniqueEmail("users-invite-undo");
        String adminToken = api.adminToken();
        long inviteId = sendInvite(adminToken, email).path("id").asLong();
        String rawToken = knownInviteToken(inviteId);
        backdateInvite(email, 1);
        String sentAt = pendingInvite(adminToken, email).path("sentAt").asText();

        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId, adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(inviteLink("check", Map.of("token", rawToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(api.authPost(INVITES + "/" + inviteId + "/restore", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        assertThat(pendingInvite(adminToken, email).path("sentAt").asText()).isEqualTo(sentAt);
        mockMvc.perform(inviteLink("check", Map.of("token", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        // Undone twice changes nothing more.
        mockMvc.perform(api.authPost(INVITES + "/" + inviteId + "/restore", adminToken))
                .andExpect(status().isOk());
        assertThat(auditEntriesAbout("AdminInvite", inviteId))
                .extracting(AuditLog::getActionType)
                .containsExactlyInAnyOrder("ADMIN_INVITE_SENT", "ADMIN_INVITE_CANCELLED", "ADMIN_INVITE_RESTORED");

        // Once accepted, the invite is an admin account: it is neither cancelled nor restored.
        mockMvc.perform(inviteLink("accept", Map.of(
                        "token", rawToken, "fullName", "Mei Admin",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(INVITES + "/" + inviteId + "/restore", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invite was already accepted."));
        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId, adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(api.authPost(INVITES + "/999999/restore", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This invite was not found."));
    }

    /** A cancelled invite sent again is pending again, with a new link. */
    @Test
    void aCancelledInviteCanBeSentAgain() throws Exception {
        String email = uniqueEmail("users-invite-resend");
        String adminToken = api.adminToken();
        long inviteId = sendInvite(adminToken, email).path("id").asLong();
        String oldToken = knownInviteToken(inviteId);
        mockMvc.perform(api.authDelete(INVITES + "/" + inviteId, adminToken))
                .andExpect(status().isOk());

        assertThat(sendInvite(adminToken, email).path("id").asLong()).isEqualTo(inviteId);
        assertThat(pendingInvite(adminToken, email).path("id").asLong()).isEqualTo(inviteId);
        // The new sending replaced the link: the one sent before stays refused.
        mockMvc.perform(inviteLink("check", Map.of("token", oldToken)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Accepting claims the link: an acceptance that read the invite before it was cancelled, sent again (a new link) or
     * accepted by an earlier click creates no admin account.
     */
    @ParameterizedTest
    @ValueSource(strings = { "cancel", "send again", "accept" })
    void anAcceptanceThatReadTheInviteBeforeItChangedCreatesNoAccount(String change) throws Exception {
        String email = uniqueEmail("users-invite-race");
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long inviteId = adminInviteService.invite(admin, email).id();
        String rawToken = knownInviteToken(inviteId);

        assertThatThrownBy(() -> withEarlierRead(transactionManager,
                () -> adminInviteRepository.findById(inviteId).orElseThrow(),
                () -> adminInviteService.accept(acceptance(rawToken)),
                () -> {
                    switch (change) {
                        case "cancel" -> adminInviteService.cancel(admin, inviteId);
                        case "send again" -> adminInviteService.invite(admin, email);
                        case "accept" -> adminInviteService.accept(acceptance(rawToken));
                        default -> throw new AssertionError(change);
                    }
                })).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(400);
                    assertThat(error.getReason())
                            .isEqualTo("This invite link is invalid or has expired. Ask an admin to send a new one.");
                });

        // Only the earlier click's account exists.
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isEqualTo(change.equals("accept"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "cancel", "restore" })
    void changesToAnInviteAcceptedSinceItWasReadAreRefused(String operation) throws Exception {
        String email = uniqueEmail("users-invite-cancel-race");
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long inviteId = adminInviteService.invite(admin, email).id();
        String rawToken = knownInviteToken(inviteId);

        assertThatThrownBy(() -> withEarlierRead(transactionManager,
                () -> adminInviteRepository.findById(inviteId).orElseThrow(),
                () -> {
                    if (operation.equals("cancel")) {
                        adminInviteService.cancel(admin, inviteId);
                    } else {
                        adminInviteService.restore(admin, inviteId);
                    }
                },
                () -> adminInviteService.accept(acceptance(rawToken))))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(409);
                    assertThat(error.getReason()).isEqualTo("This invite was already accepted.");
                });

        assertThat(userRepository.existsByEmailIgnoreCase(email)).isTrue();
        assertThat(adminInviteRepository.findById(inviteId).orElseThrow().getCancelledAt()).isNull();
        assertThat(auditEntriesAbout("AdminInvite", inviteId))
                .extracting(AuditLog::getActionType)
                .containsExactly("ADMIN_INVITE_SENT");
    }

    @Test
    void anExpiredCancelledInviteStaysCancelled() throws Exception {
        String email = uniqueEmail("users-invite-expired");
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long inviteId = adminInviteService.invite(admin, email).id();
        adminInviteService.cancel(admin, inviteId);
        jdbc.update("update admin_invites set expires_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), inviteId);

        mockMvc.perform(api.authPost(INVITES + "/" + inviteId + "/restore", api.adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invite has expired. Send a new invite."));
        assertThat(adminInviteRepository.findById(inviteId).orElseThrow().getCancelledAt()).isNotNull();
    }

    private static AcceptAdminInviteRequest acceptance(String rawToken) {
        return new AcceptAdminInviteRequest(rawToken, "Invited Admin", USER_PASSWORD, USER_PASSWORD, null);
    }

    /** The pending invite to this address, as the Users page lists it. */
    private JsonNode pendingInvite(String adminToken, String email) throws Exception {
        JsonNode pending = api.data(mockMvc.perform(api.authGet(INVITES, adminToken))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode invite : pending) {
            if (email.equals(invite.path("email").asText())) {
                return invite;
            }
        }
        throw new AssertionError("No pending invite to " + email);
    }

    /** The pending list shows when each invite was last sent, newest first: sending one again moves it to the top. */
    @Test
    void anInviteSentAgainIsTheNewestPendingInvite() throws Exception {
        String adminToken = api.adminToken();
        String first = uniqueEmail("users-invite-again");
        String second = uniqueEmail("users-invite-later");
        for (String email : List.of(first, second)) {
            sendInvite(adminToken, email);
        }
        backdateInvite(first, 2);
        backdateInvite(second, 1);
        sendInvite(adminToken, first);

        JsonNode pending = api.data(mockMvc.perform(api.authGet(INVITES, adminToken))
                .andExpect(status().isOk())
                .andReturn());
        List<String> pendingEmails = pending.findValuesAsText("email");
        assertThat(pendingEmails).containsSubsequence(first, second);
        assertThat(pendingEmails).filteredOn(first::equals).hasSize(1);
    }

    /** Sending an invite again answers with the time of this sending, not of the one it replaces. */
    @Test
    void anInviteSentAgainReportsThisSendingAsItsSentTime() throws Exception {
        String adminToken = api.adminToken();
        String email = uniqueEmail("users-invite-resent");
        sendInvite(adminToken, email);
        backdateInvite(email, 2);
        Instant resentFrom = Instant.now().minus(1, ChronoUnit.MINUTES);

        JsonNode resent = sendInvite(adminToken, email);

        assertThat(OffsetDateTime.parse(resent.path("sentAt").asText()).toInstant()).isAfter(resentFrom);
    }

    /** The newly created invite was first sent on an earlier day. */
    private void backdateInvite(String email, int daysAgo) {
        Timestamp sentAt = Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo));
        assertThat(jdbc.update("update admin_invites set created_at = ?, updated_at = ? where email = ?",
                sentAt, sentAt, email)).isOne();
    }

    /** The admin sidebar counts AI reports and open support requests; learners count their mistakes. */
    @Test
    void navCountsShowEachRoleItsOwnBadges() throws Exception {
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", api.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiReports").isNumber())
                .andExpect(jsonPath("$.data.supportOpen").isNumber())
                .andExpect(jsonPath("$.data.teacherApplications").doesNotExist())
                .andExpect(jsonPath("$.data.reviewQueue").doesNotExist())
                .andExpect(jsonPath("$.data.supportReplies").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", api.registerStudent(uniqueEmail("users-nav"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mistakes").value(0))
                .andExpect(jsonPath("$.data.supportReplies").doesNotExist());
    }

    @Test
    void onlyAdminsUseTheseEndpoints() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("users-student"));

        mockMvc.perform(api.authGet(USERS, studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost(INVITES, studentToken, Map.of("email", uniqueEmail("x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        // The teacher-application endpoints are gone.
        mockMvc.perform(api.authGet("/api/v1/admin/teacher-applications", api.adminToken()))
                .andExpect(status().isNotFound());
    }

    private JsonNode sendInvite(String adminToken, String email) throws Exception {
        return api.data(mockMvc.perform(api.jsonPost(INVITES, adminToken, Map.of("email", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn());
    }

    /** The raw link token only exists in the e-mail: give the stored invite a known token. */
    private String knownInviteToken(long inviteId) {
        String rawToken = SecureTokens.newHexToken();
        assertThat(jdbc.update("update admin_invites set token_hash = ? where id = ?",
                SecureTokens.sha256Hex(rawToken), inviteId)).isOne();
        return rawToken;
    }

    /**
     * Each invite link call ({@code check}, {@code accept}) gets its own unique remote IP so the shared per-IP rate
     * limiter never collides across tests in the cached context. The limiter itself is unit-tested separately.
     */
    private MockHttpServletRequestBuilder inviteLink(String action, Object body) throws Exception {
        return api.jsonPost("/api/v1/auth/admin-invite/" + action, null, body)
                .with(fromRemoteAddress(nextClientIp()));
    }
}
