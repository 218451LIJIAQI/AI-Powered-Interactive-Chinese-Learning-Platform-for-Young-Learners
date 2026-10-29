package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Help & support end to end: requests from students, teachers and visitors, the admin queue and its replies, the
 * Open / Answered / Solved flow with the 7-day auto-close, notifications and ownership.
 */
class SupportIntegrationTests extends ApiIntegrationTest {

    private static final String OWN = "/api/v1/users/me/support";
    private static final String ADMIN = "/api/v1/admin/support";

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void studentSendsARequestWithAScreenshotAndAdminsAreTold() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-student"));

        String body = mockMvc.perform(newRequest(token, "Lesson video won't play")
                        .file(new MockMultipartFile("screenshot", "screen.png", "image/png", TestUploadFixtures.minimalPng())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("OPEN"))
                .andExpect(jsonPath("$.data.request.requesterRole").value("STUDENT"))
                .andExpect(jsonPath("$.data.request.number").isNumber())
                .andExpect(jsonPath("$.data.messages[0].fromSupport").value(false))
                .andExpect(jsonPath("$.data.messages[0].attachment.fileName").value("screen.png"))
                .andReturn().getResponse().getContentAsString();
        Long messageId = objectMapper.readTree(body).path("data").path("messages").path(0).path("id").asLong();

        mockMvc.perform(api.authGet(OWN, token))
                .andExpect(jsonPath("$.data[0].subject").value("Lesson video won't play"));
        mockMvc.perform(api.authGet(OWN + "/attachments/" + messageId, token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getNotificationType() == NotificationType.SUPPORT_REQUEST
                        && "New support request".equals(notification.getTitle()));
    }

    @Test
    void numbersStartAt1000AndGrow() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-numbers"));
        int first = createRequest(token, "First").path("request").path("number").asInt();
        int second = createRequest(token, "Second").path("request").path("number").asInt();

        assertThat(first).isGreaterThanOrEqualTo(1000);
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void supportReplyMakesItAnsweredThenTheRequesterRepliesOrSolvesIt() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-flow"));
        Long id = createRequest(token, "Live session link didn't open").path("request").path("id").asLong();
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(ADMIN + "?status=OPEN", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.OPEN").isNumber())
                .andExpect(jsonPath("$.data.requests.items[?(@.id == " + id + ")]").exists());
        mockMvc.perform(api.jsonPost(ADMIN + "/" + id + "/replies", adminToken, Map.of("body", "Try Copy link.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.request.closesAt").exists())
                .andExpect(jsonPath("$.data.messages[1].fromSupport").value(true));
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", token))
                .andExpect(jsonPath("$.data.supportReplies").value(1));
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                "Support replied to your request".equals(notification.getTitle()));

        mockMvc.perform(api.jsonPost(OWN + "/" + id + "/replies", token, Map.of("body", "Copy link worked.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("OPEN"))
                .andExpect(jsonPath("$.data.request.closesAt").doesNotExist());
        mockMvc.perform(api.authPost(OWN + "/" + id + "/solve", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                .andExpect(jsonPath("$.data.request.closedBy").value("REQUESTER"));
        mockMvc.perform(api.jsonPost(OWN + "/" + id + "/replies", token, Map.of("body", "One more thing")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "This request is solved. Send a new request if you need more help."));
    }

    @Test
    void anAnsweredRequestClosesByItselfSevenDaysAfterTheLastSupportReply() throws Exception {
        String token = api.registerStudent(uniqueEmail("support-autoclose"));
        Long id = createRequest(token, "Quiet request").path("request").path("id").asLong();
        mockMvc.perform(api.jsonPost(ADMIN + "/" + id + "/replies", api.adminToken(), Map.of("body", "Answer")))
                .andExpect(status().isOk());

        SupportRequest request = supportRequestRepository.findById(id).orElseThrow();
        request.setClosesAt(LocalDateTime.now().minusMinutes(1));
        supportRequestRepository.save(request);
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", token))
                .andExpect(jsonPath("$.data.supportReplies").value(0));

        mockMvc.perform(api.authGet(OWN + "/" + id, token))
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                .andExpect(jsonPath("$.data.request.closedBy").value("AUTO"));
        SupportRequest closed = supportRequestRepository.findById(id).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SupportRequestStatus.SOLVED);
        assertThat(closed.getClosedBy()).isEqualTo(SupportClosedBy.AUTO);
    }

    @Test
    void visitorsWriteThroughTheHelpPageAndAdminsSolveRequests() throws Exception {
        String email = uniqueEmail("support-visitor");
        String body = mockMvc.perform(post("/api/v1/public/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "topic", "ACCOUNT",
                                "message", "Can't sign in after resetting my password\nThe new one fails."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn().getResponse().getContentAsString();
        int number = objectMapper.readTree(body).path("data").path("number").asInt();
        SupportRequest request = supportRequestRepository.findAll().stream()
                .filter(candidate -> candidate.getNumber() == number)
                .findFirst()
                .orElseThrow();
        assertThat(request.getSubject()).isEqualTo("Can't sign in after resetting my password");
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(ADMIN + "/" + request.getId(), adminToken))
                .andExpect(jsonPath("$.data.request.requesterRole").value("VISITOR"))
                .andExpect(jsonPath("$.data.request.requesterEmail").value(email))
                .andExpect(jsonPath("$.data.requesterUserId").doesNotExist());
        mockMvc.perform(api.jsonPost(ADMIN + "/" + request.getId() + "/replies", adminToken, Map.of("body", "Reset again.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(ADMIN + "/" + request.getId() + "/solve", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.status").value("SOLVED"))
                .andExpect(jsonPath("$.data.request.closedBy").value("SUPPORT"));
        mockMvc.perform(post("/api/v1/public/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email", "topic", "OTHER",
                                "message", "Hello"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestsArePrivateToTheirRequesterAndAdminsUseTheQueue() throws Exception {
        String ownerToken = api.registerStudent(uniqueEmail("support-owner"));
        String otherToken = api.registerTeacher(uniqueEmail("support-other"));
        Long id = createRequest(ownerToken, "Private").path("request").path("id").asLong();

        mockMvc.perform(api.authGet(OWN + "/" + id, otherToken)).andExpect(status().isNotFound());
        mockMvc.perform(api.authPost(OWN + "/" + id + "/solve", otherToken)).andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(ADMIN, ownerToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.authGet(OWN, api.adminToken())).andExpect(status().isForbidden());
        // Teacher applicants use Help & support too.
        mockMvc.perform(newRequest(otherToken, "Is a scanned certificate accepted?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.requesterRole").value("TEACHER"));
    }

    private MockMultipartHttpServletRequestBuilder newRequest(String token, String subject) {
        MockMultipartHttpServletRequestBuilder request = api.authMultipart(OWN, token);
        request.param("topic", "CLASSES").param("subject", subject).param("message", "It stops after 10 seconds.");
        return request;
    }

    private JsonNode createRequest(String token, String subject) throws Exception {
        return api.data(mockMvc.perform(newRequest(token, subject)).andExpect(status().isOk()).andReturn());
    }
}
