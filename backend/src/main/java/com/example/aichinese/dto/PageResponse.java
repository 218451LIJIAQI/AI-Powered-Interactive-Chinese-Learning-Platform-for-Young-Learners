package com.example.aichinese.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Generic paginated wrapper used by every list-style endpoint in the
 * platform.
 *
 * <h2>Wire format</h2>
 * <pre>{@code
 *   { "items": [ ... ],
 *     "page": 0,           // zero-based current page index
 *     "size": 20,          // requested page size
 *     "totalItems": 137,   // total matching rows across all pages
 *     "totalPages": 7 }    // ceil(totalItems / size), or 0 when empty
 * }</pre>
 *
 * <h2>Construction patterns</h2>
 * <p>The canonical 5-argument constructor is invoked directly by ~20
 * service methods that already have a {@link Page} and the sanitised
 * {@code page} / {@code size} request parameters in scope. New code
 * should prefer {@link #of(Page, int, int)} or
 * {@link #of(List, int, int, long)} — both delegate to the canonical
 * constructor and guarantee the {@link #totalPages} invariant.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Records are immutable; the embedded {@link #items} list is copied
 * defensively on entry so external mutation cannot leak into an
 * already-serialised page.</p>
 *
 * @param <T>        the type of each row in {@link #items}
 * @param items      the page contents in canonical sort order; never
 *                   {@code null}, may be empty
 * @param page       zero-based page index actually returned (after
 *                   the service layer's
 *                   {@code PaginationUtils#sanitizePage} clamping)
 * @param size       page size actually used (after
 *                   {@code PaginationUtils#sanitizeSize} clamping)
 * @param totalItems total number of items matching the query across
 *                   all pages
 * @param totalPages {@code ceil(totalItems / size)}; {@code 0} when
 *                   {@code totalItems == 0}
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    /**
     * Defensive canonical constructor: copies {@code items} so the
     * caller's mutable list cannot leak into the immutable response.
     * A {@code null} {@code items} argument is normalised to an empty
     * list so JSON serialisation produces {@code []} rather than a
     * confusing {@code null}.
     */
    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Build a page response from a Spring Data {@link Page} that has
     * already been mapped to the response type.
     *
     * @param page          the underlying Spring Data page (already
     *                      mapped to {@code T})
     * @param requestedPage the zero-based page index the caller asked
     *                      for (echoed back to the client)
     * @param requestedSize the page size the caller asked for (echoed
     *                      back to the client)
     */
    public static <T> PageResponse<T> of(Page<T> page, int requestedPage, int requestedSize) {
        return new PageResponse<>(
                page.getContent(),
                requestedPage,
                requestedSize,
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Build a page response from an in-memory list slice plus the
     * authoritative {@code totalItems} count. Computes
     * {@link #totalPages} from {@code totalItems} and {@code size},
     * returning {@code 0} when the result set is empty.
     */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = totalItems == 0 || size <= 0
                ? 0
                : (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }

    /**
     * Empty-page constant builder for endpoints that need to return a
     * well-formed envelope without performing any database query (e.g.
     * a guarded path that detects "no entitlement" early). The
     * {@code totalPages} field is forced to {@code 0} per the
     * contract above.
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0L, 0);
    }
}
