package com.example.aichinese.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Page and size clamping shared by every paged service (at most {@value #DEFAULT_MAX_PAGE_SIZE} items per page, so
 * one request cannot make the server load a whole table), plus index helpers for the few services that page a list
 * in memory. Index arithmetic is done in {@code long}, so a huge page number cannot overflow.
 */
public final class PaginationUtils {

    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    /** A negative page is the first page. */
    public static int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    /** The size clamped to {@code [1, DEFAULT_MAX_PAGE_SIZE]}. */
    public static int sanitizeSize(int size) {
        return Math.min(Math.max(size, 1), DEFAULT_MAX_PAGE_SIZE);
    }

    /** The requested page, with the page and size sanitized. */
    public static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(sanitizePage(page), sanitizeSize(size));
    }

    /** {@link #pageRequest}, newest {@code createdAt} first. */
    public static Pageable newestPageable(int page, int size) {
        return pageRequest(page, size).withSort(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** The first index of the page in a list of {@code totalItems}, at most {@code totalItems}. */
    public static int startIndex(int page, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long offset = (long) sanitizePage(page) * sanitizeSize(size);
        return (int) Math.min(offset, totalItems);
    }

    /** The end index (exclusive, for {@link java.util.List#subList}) of the page that starts at {@code startIndex}. */
    public static int endIndex(int startIndex, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long end = (long) Math.max(startIndex, 0) + sanitizeSize(size);
        return (int) Math.min(end, totalItems);
    }
}
