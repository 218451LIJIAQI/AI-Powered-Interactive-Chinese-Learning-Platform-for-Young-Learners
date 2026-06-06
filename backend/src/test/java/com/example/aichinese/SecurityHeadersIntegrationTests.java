package com.example.aichinese;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersIntegrationTests {

    private static final String HEALTH_ENDPOINT = "/api/v1/health";
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String FRAME_OPTIONS = "X-Frame-Options";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String PERMISSIONS_POLICY = "Permissions-Policy";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String NO_SNIFF = "nosniff";
    private static final String DENY = "DENY";
    private static final String STRICT_ORIGIN_WHEN_CROSS_ORIGIN = "strict-origin-when-cross-origin";
    private static final String GEOLOCATION_BLOCKED = "geolocation=()";
    private static final String DEFAULT_SELF = "default-src 'self'";
    private static final String UNSAFE_EVAL = "unsafe-eval";
    private static final String HSTS_ONE_YEAR = "max-age=31536000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicHealthResponseCarriesBrowserSecurityHeaders() throws Exception {
        mockMvc.perform(secureGet(HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().string(CONTENT_TYPE_OPTIONS, NO_SNIFF))
                .andExpect(header().string(FRAME_OPTIONS, DENY))
                .andExpect(header().string(REFERRER_POLICY, STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .andExpect(header().string(PERMISSIONS_POLICY, containsString(GEOLOCATION_BLOCKED)))
                .andExpect(header().string(CONTENT_SECURITY_POLICY, containsString(DEFAULT_SELF)))
                .andExpect(header().string(CONTENT_SECURITY_POLICY, not(containsString(UNSAFE_EVAL))))
                .andExpect(header().string(STRICT_TRANSPORT_SECURITY, containsString(HSTS_ONE_YEAR)));
    }

    private static MockHttpServletRequestBuilder secureGet(String path) {
        return get(path).secure(true);
    }
}
