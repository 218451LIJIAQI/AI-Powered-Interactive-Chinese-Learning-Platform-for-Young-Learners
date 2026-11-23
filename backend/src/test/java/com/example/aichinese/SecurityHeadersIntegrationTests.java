package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.example.aichinese.support.ApiIntegrationTest;

class SecurityHeadersIntegrationTests extends ApiIntegrationTest {

    private static final String WEB_APP_ORIGIN = "http://localhost:5174";

    @Test
    void publicHealthResponseCarriesBrowserSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-eval"))))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }

    /**
     * A web app served from another origin reads "the file is too large" only when the refusal carries the CORS
     * headers: the size check, which answers from the Content-Length alone, runs after the CORS filter.
     */
    @Test
    void anOversizedUploadIsRefusedWithTheCorsHeadersOfTheWebApp() throws Exception {
        String token = api.registerStudent(uniqueEmail("cors-oversized-upload"));

        mockMvc.perform(post("/api/v1/users/me/support")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ORIGIN, WEB_APP_ORIGIN)
                        .contentType("multipart/form-data; boundary=oversized")
                        .content(new byte[70 * 1024]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_APP_ORIGIN));
    }
}
