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

    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String METHOD_GET = "GET";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String SANITIZED_REQUEST_ID = "req____";
    private static final String UUID_PATTERN = "[0-9a-f-]{36}";

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void sanitizesReflectedRequestIdAndClearsThreadLocalState() throws Exception {
        MockHttpServletRequest request = requestWithId(" req\r\n\u4e2d\u6587 ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.requestIdDuringChain).isEqualTo(SANITIZED_REQUEST_ID);
        assertThat(chain.mdcDuringChain).isEqualTo(SANITIZED_REQUEST_ID);
        assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo(SANITIZED_REQUEST_ID);
        assertRequestContextCleared();
    }

    @Test
    void generatesFreshUuidWhenInboundRequestIdContainsNoSafeCharacters() throws Exception {
        MockHttpServletRequest request = requestWithId("\r\n\t");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.requestIdDuringChain).matches(UUID_PATTERN);
        assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo(chain.requestIdDuringChain);
        assertRequestContextCleared();
    }

    private static MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest(METHOD_GET, HEALTH_PATH);
        request.addHeader(RequestIdHolder.HEADER_NAME, requestId);
        return request;
    }

    private static void assertRequestContextCleared() {
        assertThat(RequestIdHolder.get()).isNull();
        assertThat(MDC.get(REQUEST_ID_MDC_KEY)).isNull();
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
            mdcDuringChain = MDC.get(REQUEST_ID_MDC_KEY);
        }
    }
}
