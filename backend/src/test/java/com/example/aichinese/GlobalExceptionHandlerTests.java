package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.GlobalExceptionHandler;

/**
 * Unit tests for {@link GlobalExceptionHandler} that exercise the null/blank
 * exception-message safety net. These guarantees are not covered by the
 * integration tests because no service throws an unmessaged exception today,
 * but a future caller (or library) easily could.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentExceptionWithNullMessageReturnsSafeFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException((String) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isNotBlank();
        assertThat(body.message()).doesNotContainIgnoringCase("null");
        assertThat(body.errors()).isNotEmpty();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void illegalArgumentExceptionWithBlankMessageReturnsSafeFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("   "));

        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isNotBlank();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void illegalArgumentExceptionWithMessagePreservesOriginalMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Specific business rule violated."));

        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isEqualTo("Specific business rule violated.");
        assertThat(body.errors()).containsExactly("Specific business rule violated.");
    }

    @Test
    void accessDeniedExceptionWithNullMessageReturnsForbiddenFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(
                new AccessDeniedException(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isNotBlank();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }
}
