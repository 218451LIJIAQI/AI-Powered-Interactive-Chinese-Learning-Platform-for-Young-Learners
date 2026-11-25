package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.http.HttpServletRequestWrapper;

import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MultipartRequestSizeFilterTests {

    private static final String SUPPORT_PATH = "/api/v1/users/me/support";
    private static final String LESSON_VIDEO_PATH = "/api/v1/admin/lessons/1/video";
    private static final String PRACTICE_CHECK_PATH = "/api/v1/ai/practice-checks";
    private static final DataSize VIDEO_LIMIT = DataSize.ofMegabytes(200);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UploadProperties uploads = uploadProperties();
    private final MultipartRequestSizeFilter filter = new MultipartRequestSizeFilter(uploads, objectMapper);

    @AfterEach
    void clearRequestId() {
        RequestIdHolder.unbind();
    }

    @Test
    void onlyTheAdminLessonVideoRouteGetsTheVideoLimitAndNonUploadRoutesAreRejectedWith415() throws Exception {
        assertRouteLimit(LESSON_VIDEO_PATH, VIDEO_LIMIT);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(multipartRequest("/api/v1/teachers/lessons/1/video", 128L), response, chain);
        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 15})
    void supportScreenshotsUseTheirOwnLimitOnEitherSideOfTheGeneralLimit(int megabytes) throws Exception {
        DataSize limit = DataSize.ofMegabytes(megabytes);
        uploads.setSupportScreenshotMaxFileSize(limit);

        assertRouteLimit(SUPPORT_PATH, limit);
    }

    @ParameterizedTest
    @CsvSource({"12, 18", "18, 12"})
    void practiceChecksAllowTheLargerOfTheTwoMediaLimits(int audioMegabytes, int imageMegabytes) throws Exception {
        uploads.setAiAudioMaxFileSize(DataSize.ofMegabytes(audioMegabytes));
        uploads.setAiImageMaxFileSize(DataSize.ofMegabytes(imageMegabytes));

        assertRouteLimit(PRACTICE_CHECK_PATH, DataSize.ofMegabytes(18));
    }

    @Test
    void lessonVideosKeepTheirOwnLimitUnderAnApplicationContext() throws Exception {
        MockHttpServletRequest request = multipartRequest("/school" + LESSON_VIDEO_PATH, withOverhead(VIDEO_LIMIT));
        request.setContextPath("/school");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectedRequestIncludesBoundRequestIdInApiEnvelope() throws Exception {
        RequestIdHolder.bind("oversized-upload-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(multipartRequest(LESSON_VIDEO_PATH, withOverhead(VIDEO_LIMIT) + 1),
                response, new MockFilterChain());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("requestId").asText()).isEqualTo("oversized-upload-request");
    }

    @Test
    void ignoresNonMultipartRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat/stream");
        request.setContent(new byte[128]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void chunkedMultipartUploadsWithoutContentLengthAreRejectedWithLengthRequired() throws Exception {
        MockHttpServletRequest request = multipartRequest(SUPPORT_PATH, -1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(chain.getRequest()).isNull();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asText())
                .isEqualTo("The file upload did not work. Please check the file type and size, then try again.");
    }

    @Test
    void wrappedPreParsedMultipartRequestUsesFileSizesEvenWithoutContentLength() throws Exception {
        MockMultipartHttpServletRequest small = new MockMultipartHttpServletRequest();
        small.setMethod("POST");
        small.setRequestURI(SUPPORT_PATH);
        small.addHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test");
        small.addFile(new MockMultipartFile("screenshot", "shot.png", "image/png", new byte[128]));
        MockFilterChain passChain = new MockFilterChain();

        filter.doFilter(new HttpServletRequestWrapper(small), new MockHttpServletResponse(), passChain);
        assertThat(passChain.getRequest()).isNotNull();

        uploads.setSupportScreenshotMaxFileSize(DataSize.ofBytes(100));
        MockMultipartHttpServletRequest oversized = new MockMultipartHttpServletRequest();
        oversized.setMethod("POST");
        oversized.setRequestURI(SUPPORT_PATH);
        oversized.addHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test");
        oversized.addFile(new MockMultipartFile("screenshot", "big.png", "image/png",
                new byte[(int) MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES + 101]));
        assertRejectedWithPayloadTooLarge(new HttpServletRequestWrapper(oversized));
    }

    private static UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setMaxVideoFileSize(VIDEO_LIMIT);
        return properties;
    }

    private static long withOverhead(DataSize limit) {
        return limit.toBytes() + MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES;
    }

    private void assertRouteLimit(String path, DataSize fileLimit) throws Exception {
        long requestLimit = withOverhead(fileLimit);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(multipartRequest(path, requestLimit), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertRejectedWithPayloadTooLarge(multipartRequest(path, requestLimit + 1));
    }

    private static MockHttpServletRequest multipartRequest(String path, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return contentLength;
            }
        };
        request.addHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test");
        return request;
    }

    /** The filter answers 413 with the JSON error envelope itself and never passes the request on. */
    private void assertRejectedWithPayloadTooLarge(jakarta.servlet.http.HttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asText())
                .isEqualTo("The file is too large. Please choose a smaller file and try again.");
        assertThat(chain.getRequest()).isNull();
    }
}
