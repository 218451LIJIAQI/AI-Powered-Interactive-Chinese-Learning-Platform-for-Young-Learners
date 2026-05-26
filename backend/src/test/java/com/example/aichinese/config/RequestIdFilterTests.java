package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.aichinese.util.RequestIdHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class RequestIdFilterTests {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void sanitizesReflectedRequestIdAndClearsThreadLocalState() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdHolder.HEADER_NAME, " req\r\n中文 ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.requestIdDuringChain).isEqualTo("req____");
        assertThat(chain.mdcDuringChain).isEqualTo("req____");
        assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo("req____");
        assertThat(RequestIdHolder.get()).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatesFreshUuidWhenInboundRequestIdContainsNoSafeCharacters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestIdHolder.HEADER_NAME, "\r\n\t");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).isNotBlank();
        assertThat(chain.requestIdDuringChain).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo(chain.requestIdDuringChain);
        assertThat(RequestIdHolder.get()).isNull();
    }

    private static final class CapturingFilterChain implements FilterChain {
        private boolean invoked;
        private String requestIdDuringChain;
        private String mdcDuringChain;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            invoked = true;
            requestIdDuringChain = RequestIdHolder.get();
            mdcDuringChain = MDC.get("requestId");
        }
    }
}
