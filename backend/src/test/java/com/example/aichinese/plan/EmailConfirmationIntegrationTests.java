package com.example.aichinese.plan;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.aichinese.entity.EmailConfirmationToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.support.ApiIntegrationTest;

/** A new student's e-mail confirmation: the link sent at sign-up, sending it again, and opening it. */
class EmailConfirmationIntegrationTests extends ApiIntegrationTest {

    private static final String CONFIRM = "/api/v1/auth/confirm-signup-email";
    private static final String RESEND = "/api/v1/users/me/email-confirmation";

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void signUpSendsALinkAndOpeningItConfirmsTheAddressOnce() throws Exception {
        String email = uniqueEmail("confirm-open");
        String token = api.registerStudent(email);
        User user = user(email);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(tokenRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isPresent();

        String raw = knownLink(user, LocalDateTime.now().plusDays(1));
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
        api.registerStudent(email);
        String raw = knownLink(user(email), LocalDateTime.now().minusMinutes(1));
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
        String older = knownLink(user, LocalDateTime.now().plusDays(1));
        jdbcTemplate.update("update email_confirmation_tokens set created_at = ? where user_id = ?",
                LocalDateTime.now().minusMinutes(2), user.getId());
        mockMvc.perform(api.authPost(RESEND, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.confirmed").value(false));
        mockMvc.perform(confirm(older))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendingAgainNeedsASignedInUser() throws Exception {
        mockMvc.perform(post(RESEND))
                .andExpect(status().isUnauthorized());
    }

    /** A link whose raw token the test knows (only hashes are stored). */
    private String knownLink(User user, LocalDateTime expiresAt) {
        String raw = SecureTokens.newHexToken();
        EmailConfirmationToken link = new EmailConfirmationToken();
        link.setUser(user);
        link.setEmail(user.getEmail());
        link.setTokenHash(SecureTokens.sha256Hex(raw));
        link.setExpiresAt(expiresAt);
        tokenRepository.save(link);
        return raw;
    }

    private org.springframework.test.web.servlet.RequestBuilder confirm(String raw) throws Exception {
        return post(CONFIRM).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", raw)));
    }
}
