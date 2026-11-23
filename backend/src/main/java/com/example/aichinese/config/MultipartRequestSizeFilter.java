package com.example.aichinese.config;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;

import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * Rejects obviously oversized multipart requests (and chunked multipart requests lacking {@code Content-Length}) before
 * Spring parses the request body into {@code MultipartFile} instances.
 *
 * <p>The global servlet multipart limit stays high enough for lesson videos, while this route-aware guard keeps AI
 * practice checks and every other upload on their smaller caps. Tomcat's {@code request.getParts()} reads from the
 * underlying container stream rather than a wrapped {@code getInputStream()}, so multipart uploads must declare
 * {@code Content-Length} (as browsers always do for {@code FormData}) to enforce per-route caps reliably.</p>
 *
 * <p>It runs just after the Spring Security chain: an anonymous upload is refused as unauthenticated first, and a
 * refusal here already carries the CORS headers, so a web app on another origin can read "the file is too large".
 * Nothing in the chain reads the request body.</p>
 */
@Component
@Order(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER + 1)
@RequiredArgsConstructor
public class MultipartRequestSizeFilter extends OncePerRequestFilter {

    private static final Pattern LESSON_VIDEO_PATH = Pattern.compile("/api/v1/admin/lessons/\\d+/video");
    private static final String AI_PRACTICE_CHECK_PATH = "/api/v1/ai/practice-checks";
    /**
     * Boundaries, part headers and small form fields sent with the file. Without this allowance a file
     * exactly at its documented limit (SDD 5.9) would be rejected because Content-Length covers the whole body.
     */
    static final long MULTIPART_OVERHEAD_BYTES = 64L * 1024;

    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isMultipart(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            // MockMvc pre-populates MultipartHttpServletRequest without a Content-Length header (wrapped by Spring
            // Security before reaching this filter); real Tomcat requests arrive before DispatcherServlet parses parts
            // and must declare Content-Length.
            MultipartHttpServletRequest multipartRequest =
                    WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
            if (multipartRequest != null) {
                contentLength = preParsedBytes(multipartRequest);
            } else {
                ApiErrorResponseWriter.write(
                        objectMapper,
                        response,
                        HttpStatus.LENGTH_REQUIRED,
                        FriendlyErrorMessageResolver.uploadFailedMessage());
                return;
            }
        }
        long maxRequestBytes = maxFileBytes(request) + MULTIPART_OVERHEAD_BYTES;
        if (contentLength > maxRequestBytes) {
            ApiErrorResponseWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    FriendlyErrorMessageResolver.uploadTooLargeMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static long preParsedBytes(MultipartHttpServletRequest request) {
        long total = 0L;
        for (var files : request.getMultiFileMap().values()) {
            for (MultipartFile file : files) {
                total += Math.max(0L, file.getSize());
            }
        }
        return total;
    }

    private long maxFileBytes(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (path.equals(AI_PRACTICE_CHECK_PATH)) {
            // One endpoint takes either a recording or a photo; the service checks the cap of the kind it gets.
            return Math.max(uploadProperties.getAiAudioMaxFileSize().toBytes(), uploadProperties.getAiImageMaxFileSize().toBytes());
        }
        if (LESSON_VIDEO_PATH.matcher(path).matches()) {
            return uploadProperties.getMaxVideoFileSize().toBytes();
        }
        if (path.equals("/api/v1/users/me/support")) {
            return uploadProperties.getSupportScreenshotMaxFileSize().toBytes();
        }
        return uploadProperties.getMaxFileSize().toBytes();
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        return contentType != null
                && contentType.strip().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }
}
