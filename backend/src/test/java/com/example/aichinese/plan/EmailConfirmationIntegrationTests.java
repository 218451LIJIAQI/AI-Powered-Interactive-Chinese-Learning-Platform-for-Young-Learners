package com.example.aichinese.plan;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.aichinese.entity.User;
import com.example.aichinese.service.TransactionalEmailDispatcher.EmailDeliveryRequested;
import com.example.aichinese.support.ApiIntegrationTest;

/** A new student's e-mail confirmation: the link sent at sign-up, sending it again, and opening it. */
@RecordApplicationEvents
class EmailConfirmationIntegrationTests extends ApiIntegrationTest {

    private static final String CONFIRM = "/api/v1/auth/confirm-signup-email";
    private static final String RESEND = "/api/v1/users/me/email-confirmation";

    @Autowired
    private ApplicationEvents events;

    @Test
    void signUpSendsALinkAndOpeningItConfirmsTheAddressOnce() throws Exception {
        String email = uniqueEmail("confirm-open");
        String token = api.registerStudent(email);
        User user = user(email);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from email_confirmation_tokens where user_id = ?",
                Long.class, user.getId())).isEqualTo(1L);

        String raw = latestConfirmationToken(email);
        mockMvc.perform(confirm(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        assertThat(user(email).getEmailVerifiedAt()).isNotNull();
        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(jsonPath("$.data.email.confirmed").value(true));

        mockMvc.perform(confirm(raw))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your email address is already confirmed."));
    }

    @Test
    void anExpiredOrUnknownLinkIsRefused() throws Exception {
        String email = uniqueEmail("confirm-expired");
        api.registerStudentAccount(email);
        User user = user(email);
        String raw = latestConfirmationToken(email);
        assertThat(jdbc.update("update email_confirmation_tokens set expires_at = ? where user_id = ? and used_at is null",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), user.getId())).isOne();
        mockMvc.perform(confirm(raw))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link has expired. Send a new one from Home."));
        mockMvc.perform(confirm("not-a-real-token"))
                .andExpect(status().isBadRequest());
        assertThat(user(email).getEmailVerifiedAt()).isNull();
    }

    @Test
    void theLinkCanBeSentAgainOnceAMinuteAndOnlyTheNewestWorks() throws Exception {
        String email = uniqueEmail("confirm-resend");
        String token = api.registerStudent(email);
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isTooManyRequests());

        User user = user(email);
        String older = latestConfirmationToken(email);
        ageOpenLink(user);
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.confirmed").value(false));
        mockMvc.perform(confirm(older))
                .andExpect(status().isBadRequest());
        mockMvc.perform(confirm(latestConfirmationToken(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    /** Home can say which limit stopped "Send the link again": the reason reaches the student, not a general sentence. */
    @Test
    void theStudentIsToldWhichLimitStoppedTheLink() throws Exception {
        String email = uniqueEmail("confirm-limits");
        String token = api.registerStudent(email);
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("We just sent a link. Please wait a minute before asking for another one."));

        // Five links in the last day, the newest more than a minute ago.
        User user = user(email);
        for (int link = 1; link < 5; link++) {
            ageOpenLink(user);
            mockMvc.perform(api.authPost(RESEND, token)).andExpect(status().isOk());
        }
        ageOpenLink(user);
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("You asked for several links today. Please try again tomorrow."));
    }

    @Test
    void sendingAgainNeedsASignedInUser() throws Exception {
        mockMvc.perform(post(RESEND))
                .andExpect(status().isUnauthorized());
    }

    private String latestConfirmationToken(String email) {
        String body = events.stream(EmailDeliveryRequested.class)
                .filter(message -> email.equals(message.email()))
                .map(EmailDeliveryRequested::body)
                .toList().getLast();
        String link = body.lines().filter(line -> line.startsWith("http")).findFirst().orElseThrow();
        var url = UriComponentsBuilder.fromUriString(link).build();
        assertThat(url.getPath()).endsWith("/confirm-email");
        assertThat(url.getQueryParams().getFirst("kind")).isEqualTo("signup");
        String token = url.getQueryParams().getFirst("token");
        assertThat(token).isNotBlank();
        return token;
    }

    private void ageOpenLink(User user) {
        int updated = jdbc.update("update email_confirmation_tokens set created_at = ? where user_id = ? and used_at is null",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), user.getId());
        assertThat(updated).isOne();
    }

    private RequestBuilder confirm(String raw) throws Exception {
        return api.jsonPost(CONFIRM, null, Map.of("token", raw));
    }
}
