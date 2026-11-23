package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.service.TransactionalEmailDispatcher.EmailDeliveryRequested;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Help & support end to end: a learner's "Report a problem" and a visitor's Help page request, the admin queue, its
 * replies e-mailed to the requester, the Open / Answered / Solved flow with the 7-day auto-close, solving with or
 * without a reply and its Undo (reopen), and who may do what.
 */
@RecordApplicationEvents
class SupportIntegrationTests extends ApiIntegrationTest {

    private static final String OWN = "/api/v1/users/me/support";
    private static final String ADMIN = "/api/v1/admin/support";

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    void aLearnerSendsARequestWithAScreenshotAndAdminsAreTold() throws Exception {
        String email = uniqueEmail("support-student");
        String token = api.registerStudent(email);
        byte[] screenshot = TestUploadFixtures.minimalPng();

        JsonNode created = api.data(mockMvc.perform(newRequest(token, "Lesson video won't play")
                        .file(new MockMultipartFile("screenshot", "screen.png", "image/png", screenshot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").isNumber())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.request").doesNotExist())
                .andReturn());
        Long id = supportRequestId(created.path("number").asInt());
        String adminToken = api.adminToken();

        JsonNode detail = api.data(mockMvc.perform(api.authGet(ADMIN + "/" + id, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("OPEN"))
                .andExpect(jsonPath("$.data.request.requesterRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.request.subject").value("Lesson video won't play"))
                .andExpect(jsonPath("$.data.messages[0].fromSupport").value(false))
                .andExpect(jsonPath("$.data.messages[0].attachment.fileName").value("screen.png"))
                .andReturn());
        String attachmentPath = ADMIN + "/attachments/" + detail.path("messages").path(0).path("id").asLong();
        mockMvc.perform(api.authGet(attachmentPath, adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(content().bytes(screenshot));
        mockMvc.perform(api.authGet(attachmentPath, adminToken).header(HttpHeaders.RANGE, "bytes=0-1"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-1/" + screenshot.length))
                .andExpect(content().bytes(Arrays.copyOf(screenshot, 2)));
        mockMvc.perform(api.authGet(attachmentPath, adminToken).header(HttpHeaders.RANGE, "bytes=" + screenshot.length + "-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + screenshot.length))
                .andExpect(content().bytes(new byte[0]));
        assertThat(notificationsOf(ADMIN_EMAIL)).anyMatch(notification ->
                notification.getNotificationType() == NotificationType.SUPPORT_REQUEST
                        && "New support request".equals(notification.getTitle())
                        && id.equals(notification.getTargetId()));
        // Learners send requests; the conversation goes on by e-mail, so there is no list of their own.
        mockMvc.perform(api.authGet(OWN, token)).andExpect(status().is4xxClientError());
    }

    @Test
    void numbersStartAt1000AndGrow() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-numbers"));
        int first = createRequest(token, "First");
        int second = createRequest(token, "Second");

        assertThat(first).isGreaterThanOrEqualTo(1000);
        assertThat(second).isEqualTo(first + 1);
    }

    /** Support's reply makes the request Answered and goes to the learner by e-mail (no notification). */
    @Test
    void supportRepliesByEmailToTheLearner() throws Exception {
        String email = uniqueEmail("support-flow");
        String token = api.registerStudent(email);
        int number = createRequest(token, "Lesson video won't play");
        Long id = supportRequestId(number);
        String adminToken = api.adminToken();

        List<Long> queuedIds = new ArrayList<>();
        int totalPages = 1;
        for (int page = 0; page < totalPages; page++) {
            JsonNode queue = api.data(mockMvc.perform(api.authGet(ADMIN + "?status=OPEN&page=" + page, adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.counts.OPEN").isNumber())
                    .andReturn()).path("requests");
            totalPages = queue.path("totalPages").asInt();
            for (JsonNode item : queue.path("items")) {
                queuedIds.add(item.path("id").asLong());
            }
        }
        assertThat(queuedIds).contains(id);
        mockMvc.perform(api.jsonPost(ADMIN + "/" + id + "/replies", adminToken, Map.of("body", "Try another browser.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.request.closesAt").exists())
                .andExpect(jsonPath("$.data.messages[1].fromSupport").value(true));

        List<EmailDeliveryRequested> replies = events.stream(EmailDeliveryRequested.class)
                .filter(sent -> sent.email().equals(email) && sent.subject().startsWith("Re: #"))
                .toList();
        assertThat(replies).singleElement().satisfies(reply -> {
            assertThat(reply.subject()).isEqualTo("Re: #" + number + " Lesson video won't play");
            assertThat(reply.body()).startsWith("Try another browser.");
        });
        assertThat(notificationsOf(email)).isEmpty();
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", token))
                .andExpect(jsonPath("$.data.supportReplies").doesNotExist());
    }

    @Test
    void anAnsweredRequestClosesByItselfSevenDaysAfterTheLastSupportReply() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-autoclose"));
        Long id = supportRequestId(createRequest(token, "Quiet request"));
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost(ADMIN + "/" + id + "/replies", adminToken, Map.of("body", "Answer")))
                .andExpect(status().isOk());

        SupportRequest request = supportRequestRepository.findById(id).orElseThrow();
        LocalDateTime sevenDaysAway = LocalDateTime.now().plusDays(7);
        assertThat(request.getClosesAt()).isBetween(sevenDaysAway.minusMinutes(1), sevenDaysAway.plusMinutes(1));
        request.setClosesAt(LocalDateTime.now().minusMinutes(1));
        supportRequestRepository.save(request);

        mockMvc.perform(api.authGet(ADMIN + "/" + id, adminToken))
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                .andExpect(jsonPath("$.data.request.closedBy").value("AUTO"));
        SupportRequest closed = supportRequestRepository.findById(id).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SupportRequestStatus.SOLVED);
        assertThat(closed.getClosedBy()).isEqualTo(SupportClosedBy.AUTO);
    }

    /**
     * "Mark as solved" works with or without a reply, and its Undo (reopen) puts the request back where it was: Open
     * without a reply, Answered (closing 7 days after the last reply) with one. Only a solved request reopens.
     */
    @Test
    void adminsSolveWithOrWithoutAReplyAndCanUndoIt() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-solve"));
        Long unanswered = supportRequestId(createRequest(token, "Solved without a reply"));
        Long answered = supportRequestId(createRequest(token, "Solved after a reply"));
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost(ADMIN + "/" + answered + "/replies", adminToken, Map.of("body", "Fixed now.")))
                .andExpect(status().isOk());

        mockMvc.perform(api.authPost(ADMIN + "/" + unanswered + "/reopen", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a solved request can be reopened."));
        for (Long id : List.of(unanswered, answered)) {
            mockMvc.perform(api.authPost(ADMIN + "/" + id + "/solve", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                    .andExpect(jsonPath("$.data.request.closedBy").value("SUPPORT"));
        }
        // Solving again changes nothing.
        mockMvc.perform(api.authPost(ADMIN + "/" + unanswered + "/solve", adminToken))
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"));

        mockMvc.perform(api.authPost(ADMIN + "/" + unanswered + "/reopen", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("OPEN"))
                .andExpect(jsonPath("$.data.request.closedBy").doesNotExist())
                .andExpect(jsonPath("$.data.request.solvedAt").doesNotExist())
                .andExpect(jsonPath("$.data.request.closesAt").doesNotExist());
        mockMvc.perform(api.authPost(ADMIN + "/" + answered + "/reopen", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.request.closesAt").exists());
        SupportRequest reopened = supportRequestRepository.findById(answered).orElseThrow();
        assertThat(reopened.getClosesAt()).isEqualTo(reopened.getLastSupportReplyAt().plusDays(7));
        assertThat(auditEntriesAbout("SupportRequest", answered))
                .filteredOn(entry -> "SUPPORT_REQUEST_REOPENED".equals(entry.getActionType()))
                .hasSize(1);
    }

    @Test
    void visitorsWriteThroughTheHelpPageAndAdminsSolveRequests() throws Exception {
        String email = uniqueEmail("support-visitor");
        int number = api.data(mockMvc.perform(api.jsonPost("/api/v1/public/support", null, Map.of(
                        "email", email,
                        "topic", "ACCOUNT",
                        "message", "Can't sign in after resetting my password\nThe new one fails.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn()).path("number").asInt();
        Long id = supportRequestId(number);
        SupportRequest request = supportRequestRepository.findById(id).orElseThrow();
        assertThat(request.getSubject()).isEqualTo("Can't sign in after resetting my password");
        String adminToken = api.adminToken();
        String requestPath = ADMIN + "/" + id;

        mockMvc.perform(api.authGet(requestPath, adminToken))
                .andExpect(jsonPath("$.data.request.requesterRole").value("VISITOR"))
                .andExpect(jsonPath("$.data.request.requesterEmail").value(email))
                .andExpect(jsonPath("$.data.requesterUserId").doesNotExist());
        mockMvc.perform(api.jsonPost(requestPath + "/replies", adminToken, Map.of("body", "Reset again.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(requestPath + "/solve", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                .andExpect(jsonPath("$.data.request.closedBy").value("SUPPORT"));
        mockMvc.perform(api.jsonPost(requestPath + "/replies", adminToken, Map.of("body", "One more")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This request is solved. Reopen it first if you need to reply."));
        mockMvc.perform(api.jsonPost("/api/v1/public/support", null,
                        Map.of("email", "not-an-email", "topic", "OTHER", "message", "Hello")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost("/api/v1/public/support", null,
                        Map.of("email", email, "topic", "OTHER", "message", "\u00a0\u2007\ufeff")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void learnersSendRequestsAndOnlyAdminsUseTheQueue() throws Exception {
        String ownerToken = api.registerStudent(uniqueEmail("support-owner"));
        Long id = supportRequestId(createRequest(ownerToken, "Private"));
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(ADMIN, ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(ADMIN + "/" + id, ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authPost(ADMIN + "/" + id + "/solve", ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authPost(ADMIN + "/" + id + "/reopen", ownerToken)).andExpect(status().isForbidden());
        // The learner's own replies and "solved" are gone: the conversation goes on by e-mail.
        mockMvc.perform(api.authPost(OWN + "/" + id + "/solve", ownerToken)).andExpect(status().isNotFound());
        // Admins answer requests; they don't send them.
        mockMvc.perform(newRequest(adminToken, "From an admin")).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownTopicIsRefusedWithoutJavaTypeNames() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-bad-topic"));
        MockMultipartHttpServletRequestBuilder request = api.authMultipart(OWN, token);
        request.param("topic", "FOO").param("subject", "Unknown topic").param("message", "It stops after 10 seconds.");

        String body = mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "The information you entered is incomplete or invalid. Please check it and try again."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("java.", "SupportTopic", "convert", "com.example");

        MockMultipartHttpServletRequestBuilder retiredTopic = api.authMultipart(OWN, token);
        retiredTopic.param("topic", "VERIFICATION").param("subject", "Retired topic").param("message", "Legacy value.");
        mockMvc.perform(retiredTopic).andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost("/api/v1/public/support", null,
                        Map.of("email", uniqueEmail("support-retired"), "topic", "VERIFICATION", "message", "Legacy.")))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartHttpServletRequestBuilder newRequest(String token, String subject) {
        MockMultipartHttpServletRequestBuilder request = api.authMultipart(OWN, token);
        request.param("topic", "CLASSES").param("subject", subject).param("message", "It stops after 10 seconds.");
        return request;
    }

    private int createRequest(String token, String subject) throws Exception {
        return api.data(mockMvc.perform(newRequest(token, subject)).andExpect(status().isOk()).andReturn())
                .path("number").asInt();
    }
}
