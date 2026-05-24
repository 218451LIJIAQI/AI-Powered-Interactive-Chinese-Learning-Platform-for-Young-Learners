package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.PaginationUtils;

/**
 * Unit coverage for {@code PaginationUtils} — pins the floor/ceiling
 * clamps ({@code page} ≥ 0, {@code size} bounded by min/max) and the
 * overflow protection that keeps pagination math stable even for
 * pathological inputs (Integer.MAX_VALUE etc.).
 */
class PaginationUtilsTests {

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
        assertThat(PaginationUtils.sanitizeSize(100_000)).isEqualTo(100);
    }

    @Test
    void sanitizeSizeRejectsInvalidMaximum() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaginationUtils.sanitizeSize(10, 0))
                .withMessage("maxSize must be positive.");
    }

    @Test
    void startAndEndIndexAvoidIntegerOverflowForVeryLargePages() {
        int startIndex = PaginationUtils.startIndex(Integer.MAX_VALUE, 100_000, 50);
        int endIndex = PaginationUtils.endIndex(startIndex, 100_000, 50);

        assertThat(startIndex).isEqualTo(50);
        assertThat(endIndex).isEqualTo(50);
    }

    @Test
    void totalPagesUsesSanitizedPageSize() {
        assertThat(PaginationUtils.totalPages(250, 100_000)).isEqualTo(3);
        assertThat(PaginationUtils.totalPages(0, 20)).isZero();
    }
}
