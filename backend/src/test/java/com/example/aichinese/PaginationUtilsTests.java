package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    void pageRequestFloorsNegativePagesToZero() {
        assertThat(PaginationUtils.pageRequest(-10, 25).getPageNumber()).isZero();
        assertThat(PaginationUtils.pageRequest(3, 25).getPageNumber()).isEqualTo(3);
    }

    @Test
    void pageRequestBoundsSizesToDefaultMaximum() {
        assertThat(PaginationUtils.pageRequest(0, -5).getPageSize()).isEqualTo(1);
        assertThat(PaginationUtils.pageRequest(0, 0).getPageSize()).isEqualTo(1);
        assertThat(PaginationUtils.pageRequest(0, 25).getPageSize()).isEqualTo(25);
        assertThat(PaginationUtils.pageRequest(0, OVERSIZED_REQUEST_SIZE).getPageSize()).isEqualTo(100);
    }

    @Test
    void aPageBeyondTheJpaOffsetLimitReadsOnlyTheFilteredTotal() {
        var requested = PaginationUtils.pageRequest(Integer.MAX_VALUE, OVERSIZED_REQUEST_SIZE);
        List<Pageable> queries = new ArrayList<>();
        Page<String> page = PaginationUtils.readPage(requested, query -> {
            queries.add(query);
            return new PageImpl<>(List.of("first match"), query, 50);
        });

        assertThat(queries).containsExactly(PageRequest.of(0, 1));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.getSize()).isEqualTo(PaginationUtils.DEFAULT_MAX_PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(50);
    }

    /** A page reports its page count from the sanitized size, as every paged service builds it. */
    @Test
    void pageResponseCountsPagesOfTheSanitizedSize() {
        int size = PaginationUtils.pageRequest(0, OVERSIZED_REQUEST_SIZE).getPageSize();
        assertThat(new PageResponse<>(List.of(), 0, size, 250).totalPages()).isEqualTo(3);
        assertThat(new PageResponse<>(List.of(), 0, 25, 0).totalPages()).isZero();
        assertThat(new PageResponse<>(List.of(), 0, 2, Long.MAX_VALUE).totalPages()).isEqualTo(Long.MAX_VALUE / 2 + 1);
    }
}
