package com.example.aichinese.config;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects obviously oversized multipart requests before Spring parses the
 * request body into {@code MultipartFile} instances.
 *
 * <p>The global servlet multipart limit stays high enough for lesson videos,
 * while this route-aware guard keeps verification documents, AI practice
 * checks and every other upload on their smaller caps.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MultipartRequestSizeFilter extends OncePerRequestFilter {

    private static final Pattern LESSON_VIDEO_PATH = Pattern.compile("/api/v1/teachers/lessons/\\d+/video");
    private static final String VERIFICATION_DOCUMENTS_PATH = "/api/v1/teachers/verification-documents";
    private static final String AI_PRACTICE_CHECK_PATH = "/api/v1/ai/practice-checks";
    /**
     * Boundaries, part headers and small form fields sent with the file. Without this allowance a file
     * exactly at its documented limit (SDD 5.9) would be rejected because Content-Length covers the whole body.
     */
    static final long MULTIPART_OVERHEAD_BYTES = 64L * 1024;

    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;

    public MultipartRequestSizeFilter(UploadProperties uploadProperties, ObjectMapper objectMapper) {
        this.uploadProperties = uploadProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long maxRequestBytes = maxFileBytes(RequestPaths.applicationPath(request)) + MULTIPART_OVERHEAD_BYTES;
        if (isMultipart(request) && request.getContentLengthLong() > maxRequestBytes) {
            ApiErrorResponseWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    FriendlyErrorMessageResolver.uploadTooLargeMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private long maxFileBytes(String path) {
        if (path.startsWith(VERIFICATION_DOCUMENTS_PATH)) {
            return uploadProperties.getVerificationDocumentMaxFileSize().toBytes();
        }
        if (path.equals(AI_PRACTICE_CHECK_PATH)) {
            // One endpoint takes either a recording or a photo; the service checks the cap of the kind it gets.
            return Math.max(uploadProperties.getAiAudioMaxFileSize().toBytes(), uploadProperties.getAiImageMaxFileSize().toBytes());
        }
        if (LESSON_VIDEO_PATH.matcher(path).matches()) {
            return uploadProperties.getMaxVideoFileSize().toBytes();
        }
        return uploadProperties.getMaxFileSize().toBytes();
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        return contentType != null
                && contentType.strip().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }
}
