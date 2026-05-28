package com.example.aichinese.util;

/**
 * Side-effect-free helpers used by every service that paginates a result
 * set in JVM memory (cross-type unified feeds, in-memory merge-sorts of
 * heterogeneous repositories, etc.).
 *
 * <p>Spring Data's {@link org.springframework.data.domain.Pageable} is
 * preferred whenever the database can do the slicing; these helpers only
 * exist for the rarer case where the service must merge several
 * repository results into a single in-memory list and then return a page
 * of the merged result. Every method is defensive about bad caller input
 * (negative pages, zero-or-negative sizes, out-of-range start indexes)
 * and uses {@code long} arithmetic to prevent integer overflow when the
 * caller passes large values.</p>
 */
public final class PaginationUtils {

    /**
     * Conservative upper bound on a page size. Larger requests are
     * silently clamped down — preventing a single client from forcing the
     * server to materialise tens of thousands of rows in a single
     * response.
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
        throw new AssertionError("PaginationUtils is a utility class and must not be instantiated.");
    }

    /**
     * Clamps a zero-or-positive page index, treating any negative input as
     * the first page ({@code 0}). Mirrors Spring Data's behaviour where
     * {@code Pageable.unpaged()} maps to page {@code 0}.
     */
    public static int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    /**
     * Equivalent to {@link #sanitizeSize(int, int)} with the default cap
     * of {@link #DEFAULT_MAX_PAGE_SIZE}.
     */
    public static int sanitizeSize(int size) {
        return sanitizeSize(size, DEFAULT_MAX_PAGE_SIZE);
    }

    /**
     * Clamps a page size into the inclusive range {@code [1, maxSize]} —
     * zero / negative values are bumped to {@code 1} so the caller always
     * gets at least one row, and anything above {@code maxSize} is capped.
     *
     * @param size    caller-supplied page size; any value is accepted
     * @param maxSize hard upper bound; must be {@code >= 1}
     * @return clamped page size in {@code [1, maxSize]}
     * @throws IllegalArgumentException if {@code maxSize} is less than 1
     */
    public static int sanitizeSize(int size, int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be positive.");
        }
        return Math.min(Math.max(size, 1), maxSize);
    }

    /**
     * Computes the inclusive start index of the requested page in a list
     * of {@code totalItems} elements, clamped to never exceed
     * {@code totalItems}. Uses {@code long} arithmetic so a malicious
     * {@code page} value (e.g. {@code Integer.MAX_VALUE}) cannot overflow
     * the multiplication.
     *
     * @return start index in {@code [0, totalItems]}
     */
    public static int startIndex(int page, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long offset = (long) sanitizePage(page) * sanitizeSize(size);
        return (int) Math.min(offset, totalItems);
    }

    /**
     * Computes the exclusive end index of the requested page given a
     * pre-resolved {@code startIndex}, clamped to never exceed
     * {@code totalItems}. Negative {@code startIndex} is treated as
     * {@code 0} so the helper is safe to call before
     * {@link #startIndex(int, int, int)}.
     *
     * @return end index in {@code [0, totalItems]}, suitable for
     *         {@link java.util.List#subList(int, int)}
     */
    public static int endIndex(int startIndex, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long end = (long) Math.max(startIndex, 0) + sanitizeSize(size);
        return (int) Math.min(end, totalItems);
    }

    /**
     * Computes the {@code ceil(totalItems / size)} total-page count,
     * defaulting size through {@link #sanitizeSize(int)} so a bogus
     * caller value cannot produce a divide-by-zero. Guards against
     * pathological inputs by saturating the result at
     * {@link Integer#MAX_VALUE}.
     *
     * @param totalItems non-negative total row count; values {@code <= 0}
     *                   return {@code 0}
     * @param size       requested page size; clamped via
     *                   {@link #sanitizeSize(int)}
     * @return non-negative total page count
     */
    public static int totalPages(long totalItems, int size) {
        if (totalItems <= 0) {
            return 0;
        }
        int safeSize = sanitizeSize(size);
        long pages = (totalItems + safeSize - 1L) / safeSize;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
