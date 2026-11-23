package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.aichinese.util.RequestIdHolder;

import jakarta.servlet.FilterChain;

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

    /** A cut that ends in a space: the header, the log context and the response body carry the same id. */
    @Test
    void stripsTheRequestIdAgainAfterCuttingItToLength() throws Exception {
        String inbound = "r".repeat(119) + " tail";
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(requestWithId(inbound), response, chain);

        String expected = "r".repeat(119);
        assertThat(chain.requestIdDuringChain).isEqualTo(expected);
        assertThat(chain.mdcDuringChain).isEqualTo(expected);
        assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo(expected);
        assertRequestContextCleared();
    }

    @Test
    void neutralizesStructuredLogDelimitersInInboundRequestId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(requestWithId("abc] method=POST [x="), response, chain);

        assertThat(chain.requestIdDuringChain).isEqualTo("abc__method_POST__x_");
        assertRequestContextCleared();
    }

    @Test
    void generatesFreshUuidWhenInboundRequestIdContainsNoSafeCharacters() throws Exception {
        for (String unsafe : new String[] { "\r\n\t", "\u0000\u0007", "\u4e2d\u6587" }) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            CapturingFilterChain chain = new CapturingFilterChain();

            filter.doFilter(requestWithId(unsafe), response, chain);

            assertThat(chain.requestIdDuringChain).matches(UUID_PATTERN);
            assertThat(response.getHeader(RequestIdHolder.HEADER_NAME)).isEqualTo(chain.requestIdDuringChain);
            assertRequestContextCleared();
        }
    }

    @Test
    void clearsRequestContextWhenDownstreamFilterThrows() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(
                requestWithId("req-throws"),
                new MockHttpServletResponse(),
                (req, res) -> {
                    throw new RuntimeException("boom");
                })).isInstanceOf(RuntimeException.class);

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
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
            requestIdDuringChain = RequestIdHolder.get();
            mdcDuringChain = MDC.get(REQUEST_ID_MDC_KEY);
        }
    }
}
