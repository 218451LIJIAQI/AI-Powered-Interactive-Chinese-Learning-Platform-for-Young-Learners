package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.util.PaginationUtils;

/**
 * Unit coverage for {@code PaginationUtils} - pins the floor/ceiling
 * clamps ({@code page} >= 0, {@code size} bounded by min/max) and the
 * overflow protection that keeps pagination math stable even for
 * pathological inputs (Integer.MAX_VALUE etc.).
 */
class PaginationUtilsTests {

    private static final int OVERSIZED_REQUEST_SIZE = 100_000;

    @Test
    void sanitizePageFloorsNegativePagesToZero() {
        assertThat(PaginationUtils.sanitizePage(-10)).isZero();
        assertThat(PaginationUtils.sanitizePage(3)).isEqualTo(3);
    }

    @Test
    void sanitizeSizeBoundsValuesToDefaultMaximum() {
        assertThat(PaginationUtils.sanitizeSize(-5)).isEqualTo(1);
        assertThat(PaginationUtils.sanitizeSize(0)).isEqualTo(1);
        assertThat(PaginationUtils.sanitizeSize(25)).isEqualTo(25);
        assertThat(PaginationUtils.sanitizeSize(OVERSIZED_REQUEST_SIZE)).isEqualTo(100);
    }

    @Test
    void startAndEndIndexAvoidIntegerOverflowForVeryLargePages() {
        int startIndex = PaginationUtils.startIndex(Integer.MAX_VALUE, OVERSIZED_REQUEST_SIZE, 50);
        int endIndex = PaginationUtils.endIndex(startIndex, OVERSIZED_REQUEST_SIZE, 50);

        assertThat(startIndex).isEqualTo(50);
        assertThat(endIndex).isEqualTo(50);
    }

    /** A page reports its page count from the sanitized size, as every paged service builds it. */
    @Test
    void pageResponseCountsPagesOfTheSanitizedSize() {
        int size = PaginationUtils.sanitizeSize(OVERSIZED_REQUEST_SIZE);
        assertThat(PageResponse.of(List.of(), 0, size, 250).totalPages()).isEqualTo(3);
        assertThat(PageResponse.of(List.of(), 0, 25, 0).totalPages()).isZero();
    }
}
