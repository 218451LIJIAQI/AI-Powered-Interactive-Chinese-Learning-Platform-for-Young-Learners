package com.example.aichinese.util;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared page and size bounds for paged services, with at most {@value #DEFAULT_MAX_PAGE_SIZE} items in a response.
 */
public final class PaginationUtils {

    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    /** Newest first; the id keeps rows of the same time in one order, so no row moves between pages. */
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private PaginationUtils() {
    }

    /** The requested page: a negative page is the first page, and the size is clamped to [1, DEFAULT_MAX_PAGE_SIZE]. */
    public static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, DEFAULT_MAX_PAGE_SIZE));
    }

    /** {@link #pageRequest}, sorted {@link #NEWEST_FIRST}. */
    public static PageRequest newestFirst(int page, int size) {
        return pageRequest(page, size).withSort(NEWEST_FIRST);
    }

    /** JPA accepts a signed int offset. Out-of-range pages still report their requested number and filtered total. */
    public static <T> Page<T> readPage(Pageable requested, Function<Pageable, Page<T>> query) {
        if (requested.getOffset() <= Integer.MAX_VALUE) {
            return query.apply(requested);
        }
        long total = query.apply(PageRequest.of(0, 1)).getTotalElements();
        return new PageImpl<>(List.of(), requested, total);
    }
}
