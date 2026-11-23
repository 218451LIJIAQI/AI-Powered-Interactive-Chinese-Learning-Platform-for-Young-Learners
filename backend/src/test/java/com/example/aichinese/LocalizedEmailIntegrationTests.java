package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.nextClientIp;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.service.TransactionalEmailDispatcher.EmailDeliveryRequested;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * E-mails in their reader's language, end to end: the interface language switch keeps the account's language current
 * (without touching the name) and the e-mails follow it, a learner's support reply included; a visitor's support reply
 * follows the Help page language the visitor wrote from; an admin invite follows the inviting admin, and the new admin
 * keeps the language they accepted in.
 */
@RecordApplicationEvents
class LocalizedEmailIntegrationTests extends ApiIntegrationTest {

    private static final String PROFILE = "/api/v1/users/me/profile";
    private static final Pattern INVITE_TOKEN = Pattern.compile("[?&]invite=([A-Za-z0-9]+)");

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Test
    void theLanguageSwitchIsKeptWithoutTheNameAndTheEmailsFollowIt() throws Exception {
        String email = uniqueEmail("mail-language-switch");
        String token = api.registerStudent(email);
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of("displayName", "Nur Aina", "preferredLanguage", "en")))
                .andExpect(status().isOk());

        // The switch sends the language alone: the name stays.
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of("preferredLanguage", "ms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nur Aina"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"));
        var account = user(email);
        assertThat(account.getPreferredLanguage()).isEqualTo("ms");
        assertThat(account.getDisplayName()).isEqualTo("Nur Aina");
        // A name saved from another device keeps the language used for the next e-mail.
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of("displayName", "Aina Hassan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Aina Hassan"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"));
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of()))
                .andExpect(status().isBadRequest());
        // A name that is sent must still be a name.
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of("displayName", "  ", "preferredLanguage", "ms")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName[0]").value("Display name is required."));

        // The sign-up confirmation went out in English, the language the account was created in.
        assertThat(sentTo(email)).extracting(EmailDeliveryRequested::subject).containsExactly("Confirm your email address");

        String forgotIp = nextClientIp();
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(forgotIp)))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPut(PROFILE, token, Map.of("preferredLanguage", "zh-CN"))).andExpect(status().isOk());
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(forgotIp)))
                .andExpect(status().isOk());

        assertThat(sentTo(email)).extracting(EmailDeliveryRequested::subject).containsExactly(
                "Confirm your email address", "Permintaan tetapan semula kata laluan", "密码重置请求");
    }

    /**
     * Support's reply to a learner's "Report a problem" goes to the account's address, in the account's language; it
     * is how the learner hears back (no notification).
     */
    @Test
    void aLearnersReplyIsInTheAccountsLanguage() throws Exception {
        String studentEmail = uniqueEmail("mail-language-learner");
        String studentToken = api.registerStudent(studentEmail);
        mockMvc.perform(api.jsonPut(PROFILE, studentToken, Map.of("preferredLanguage", "zh-CN")))
                .andExpect(status().isOk());
        int number = api.data(mockMvc.perform(api.authMultipart("/api/v1/users/me/support", studentToken)
                                .param("topic", "CLASSES").param("subject", "视频无法播放").param("message", "It stops."))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.email").value(studentEmail))
                        .andReturn())
                .path("number").asInt();

        mockMvc.perform(api.jsonPost("/api/v1/admin/support/" + supportRequestId(number) + "/replies", api.adminToken(),
                        Map.of("body", "Try another browser.")))
                .andExpect(status().isOk());

        // Besides the sign-up confirmation, sent in English before the switch.
        EmailDeliveryRequested reply = single(sentTo(studentEmail).stream()
                .filter(email -> !email.subject().equals("Confirm your email address"))
                .toList());
        assertThat(reply.subject()).isEqualTo("Re: #" + number + " 视频无法播放");
        assertThat(reply.body())
                .startsWith("Try another browser.")
                .endsWith("MandarinSpark 客服。直接回复这封邮件，或在帮助页面给我们留言。");
        assertThat(notificationsOf(studentEmail)).isEmpty();
    }

    @Test
    void aVisitorsReplyIsInTheLanguageTheyWroteFrom() throws Exception {
        String chineseVisitor = uniqueEmail("mail-visitor-zh");
        String unknownVisitor = uniqueEmail("mail-visitor-fr");
        SupportRequest chinese = visitorRequest(chineseVisitor, "zh-CN");
        SupportRequest unknown = visitorRequest(unknownVisitor, "fr");
        assertThat(chinese.getRequesterLanguage()).isEqualTo("zh-CN");
        assertThat(unknown.getRequesterLanguage()).isNull();

        String adminToken = api.adminToken();
        for (SupportRequest request : List.of(chinese, unknown)) {
            mockMvc.perform(api.jsonPost("/api/v1/admin/support/" + request.getId() + "/replies", adminToken,
                            Map.of("body", "Please reset your password again.")))
                    .andExpect(status().isOk());
        }

        EmailDeliveryRequested chineseReply = single(sentTo(chineseVisitor));
        assertThat(chineseReply.subject()).isEqualTo("Re: #" + chinese.getNumber() + " 我无法登录");
        assertThat(chineseReply.body())
                .startsWith("Please reset your password again.")
                .endsWith("MandarinSpark 客服。直接回复这封邮件，或在帮助页面给我们留言。");
        assertThat(single(sentTo(unknownVisitor)).body())
                .endsWith("MandarinSpark support. Reply to this email or send us a message on the Help page.");
    }

    /**
     * The invitee has no account yet: the invite is in the inviting admin's language. The new admin's account takes
     * the language of the page they accepted on (English when none is sent).
     */
    @Test
    void anAdminInviteFollowsTheInviterAndTheNewAdminKeepsTheirOwnLanguage() throws Exception {
        String malayAdmin = uniqueEmail("mail-invite-ms-admin");
        String inviteToken = invite(api.adminToken(), malayAdmin);
        assertThat(single(sentTo(malayAdmin)).subject()).isEqualTo("You are invited to be a MandarinSpark admin");
        accept(inviteToken, "Farid Admin", "ms");
        assertThat(user(malayAdmin).getPreferredLanguage()).isEqualTo("ms");

        String invitedByMalayAdmin = uniqueEmail("mail-invite-by-ms");
        String secondToken = invite(api.login(malayAdmin, USER_PASSWORD), invitedByMalayAdmin);
        EmailDeliveryRequested sent = single(sentTo(invitedByMalayAdmin));
        assertThat(sent.subject()).isEqualTo("Anda dijemput menjadi pentadbir MandarinSpark");
        assertThat(sent.body()).startsWith("Farid Admin menjemput anda untuk membantu menguruskan MandarinSpark.");
        accept(secondToken, "Siti Admin", null);
        assertThat(user(invitedByMalayAdmin).getPreferredLanguage()).isEqualTo("en");
    }

    private SupportRequest visitorRequest(String email, String language) throws Exception {
        int number = api.data(mockMvc.perform(api.jsonPost("/api/v1/public/support", null, Map.of(
                                "email", email, "topic", "ACCOUNT", "message", "我无法登录\nThe new password fails.",
                                "language", language))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk())
                .andReturn()).path("number").asInt();
        return supportRequestRepository.findById(supportRequestId(number)).orElseThrow();
    }

    /** Sends an admin invite and returns the token of the link it e-mails. */
    private String invite(String adminToken, String email) throws Exception {
        mockMvc.perform(api.jsonPost("/api/v1/admin/admin-invites", adminToken, Map.of("email", email)))
                .andExpect(status().isOk());
        Matcher link = INVITE_TOKEN.matcher(single(sentTo(email)).body());
        assertThat(link.find()).as("the invite e-mail holds its link").isTrue();
        return link.group(1);
    }

    private void accept(String inviteToken, String fullName, String language) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "token", inviteToken, "fullName", fullName, "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD));
        if (language != null) {
            body.put("preferredLanguage", language);
        }
        mockMvc.perform(api.jsonPost("/api/v1/auth/admin-invite/accept", null, body)
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk());
    }

    /** The e-mails queued to {@code email} during this test, in order. */
    private List<EmailDeliveryRequested> sentTo(String email) {
        return events.stream(EmailDeliveryRequested.class).filter(sent -> sent.email().equals(email)).toList();
    }

    private static EmailDeliveryRequested single(List<EmailDeliveryRequested> sent) {
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }
}
