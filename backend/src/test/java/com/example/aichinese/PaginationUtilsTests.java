package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.PaginationUtils;

/**
 * Unit coverage for {@code PaginationUtils} - pins the floor/ceiling
 * clamps ({@code page} >= 0, {@code size} bounded by min/max) and the
 * overflow protection that keeps pagination math stable even for
 * pathological inputs (Integer.MAX_VALUE etc.).
 */
class PaginationUtilsTests {

    private static final int MIN_PAGE = 0;
    private static final int MIN_SIZE = 1;
    private static final int NEGATIVE_PAGE = -10;
    private static final int NEGATIVE_SIZE = -5;
    private static final int DEFAULT_MAX_SIZE = 100;
    private static final int OVERSIZED_REQUEST_SIZE = 100_000;
    private static final int VALID_PAGE = 3;
    private static final int VALID_SIZE = 25;
    private static final int LAST_VALID_INDEX = 50;
    private static final int TOTAL_ITEMS = 250;
    private static final int EMPTY_TOTAL_ITEMS = 0;
    private static final int EXPECTED_TOTAL_PAGES = 3;
    private static final int INVALID_MAX_SIZE = 0;

    @Test
    void sanitizePageFloorsNegativePagesToZero() {
        assertThat(PaginationUtils.sanitizePage(NEGATIVE_PAGE)).isZero();
        assertThat(PaginationUtils.sanitizePage(VALID_PAGE)).isEqualTo(VALID_PAGE);
    }

    @Test
    void sanitizeSizeBoundsValuesToDefaultMaximum() {
        assertThat(PaginationUtils.sanitizeSize(NEGATIVE_SIZE)).isEqualTo(MIN_SIZE);
        assertThat(PaginationUtils.sanitizeSize(MIN_PAGE)).isEqualTo(MIN_SIZE);
        assertThat(PaginationUtils.sanitizeSize(VALID_SIZE)).isEqualTo(VALID_SIZE);
        assertThat(PaginationUtils.sanitizeSize(OVERSIZED_REQUEST_SIZE)).isEqualTo(DEFAULT_MAX_SIZE);
    }

    @Test
    void sanitizeSizeRejectsInvalidMaximum() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaginationUtils.sanitizeSize(VALID_SIZE, INVALID_MAX_SIZE))
                .withMessage("maxSize must be positive.");
    }

    @Test
    void startAndEndIndexAvoidIntegerOverflowForVeryLargePages() {
        int startIndex = PaginationUtils.startIndex(Integer.MAX_VALUE, OVERSIZED_REQUEST_SIZE, LAST_VALID_INDEX);
        int endIndex = PaginationUtils.endIndex(startIndex, OVERSIZED_REQUEST_SIZE, LAST_VALID_INDEX);

        assertThat(startIndex).isEqualTo(LAST_VALID_INDEX);
        assertThat(endIndex).isEqualTo(LAST_VALID_INDEX);
    }

    @Test
    void totalPagesUsesSanitizedPageSize() {
        assertThat(PaginationUtils.totalPages(TOTAL_ITEMS, OVERSIZED_REQUEST_SIZE)).isEqualTo(EXPECTED_TOTAL_PAGES);
        assertThat(PaginationUtils.totalPages(EMPTY_TOTAL_ITEMS, VALID_SIZE)).isZero();
    }
}
