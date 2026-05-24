package com.example.aichinese.dto;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <p>The component order above is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Construction patterns</h2>
 * <p>The canonical 5-argument constructor is invoked directly by ~20
 * service methods that already have a {@link Page} and the sanitised
 * {@code page} / {@code size} request parameters in scope. New code
 * should prefer {@link #of(Page, int, int)} or
 * {@link #of(List, int, int, long)} — both delegate to the canonical
 * constructor and guarantee the {@link #totalPages} invariant.</p>
 *
 * <h2>Invariants enforced by the canonical constructor</h2>
 * <ol>
 *   <li>{@link #items} is non-{@code null}; a {@code null} argument is
 *       silently substituted with an empty list so JSON serialisation
 *       produces {@code []} rather than a confusing {@code null}.</li>
 *   <li>{@link #items} is deeply immutable — the supplied list is
 *       defensively copied via {@link List#copyOf(java.util.Collection)
 *       List.copyOf} so a caller mutating the original list afterwards
 *       cannot affect the already-serialised payload.</li>
 *   <li>{@link #page}, {@link #size}, {@link #totalItems} and
 *       {@link #totalPages} are clamped to be non-negative; a programming
 *       error or malformed deserialisation that yields a negative value
 *       is silently normalised so the wire format never exposes
 *       impossible counts to the frontend.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>Records are immutable; the embedded {@link #items} list is copied
 * defensively on entry so external mutation cannot leak into an
 * already-serialised page. {@link List#copyOf} short-circuits when the
 * input is already an unmodifiable list, so the typical hot path
 * (services that already hand back unmodifiable {@code .toList()}
 * results) allocates nothing.</p>
 *
 * @param <T>        the type of each row in {@link #items}
 * @param items      the page contents in canonical sort order; never
 *                   {@code null}, may be empty
 * @param page       zero-based page index actually returned (after the
 *                   service layer's {@code PaginationUtils#sanitizePage}
 *                   clamping); always non-negative
 * @param size       page size actually used (after the service layer's
 *                   {@code PaginationUtils#sanitizeSize} clamping);
 *                   always non-negative
 * @param totalItems total number of items matching the query across
 *                   all pages; always non-negative
 * @param totalPages {@code ceil(totalItems / size)}; {@code 0} when
 *                   {@code totalItems == 0} or when the caller passes a
 *                   degenerate {@code size <= 0}
 */
@JsonPropertyOrder({ "items", "page", "size", "totalItems", "totalPages" })
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    /**
     * Defensive canonical constructor: enforces every invariant
     * documented on the class Javadoc in a single pass. Uses
     * {@link Math#max(int, int)} / {@link Math#max(long, long)} for the
     * numeric clamps so a maliciously deserialised negative count
     * cannot reach the wire format.
     */
    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        page = Math.max(page, 0);
        size = Math.max(size, 0);
        totalItems = Math.max(totalItems, 0L);
        totalPages = Math.max(totalPages, 0);
    }

    /**
     * Build a page response from a Spring Data {@link Page} that has
     * already been mapped to the response type. Spring Data computes
     * {@code totalElements} and {@code totalPages} authoritatively, so
     * this factory simply forwards them — recomputing would risk
     * disagreeing with whatever {@link Page} implementation the
     * repository chose (slice vs. full count, etc.).
     *
     * @param page          the underlying Spring Data page (already
     *                      mapped to {@code T}); must not be {@code null}
     * @param requestedPage the zero-based page index the caller asked
     *                      for (echoed back to the client)
     * @param requestedSize the page size the caller asked for (echoed
     *                      back to the client)
     */
    public static <T> PageResponse<T> of(Page<T> page, int requestedPage, int requestedSize) {
        Objects.requireNonNull(page, "page");
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
     * {@link #totalPages} from {@code totalItems} and {@code size}
     * using {@code long} arithmetic so a {@code totalItems} near
     * {@link Long#MAX_VALUE} cannot lose precision through a
     * {@code double} cast and the result cannot overflow {@code int}
     * — pathological values saturate at {@link Integer#MAX_VALUE}
     * instead of wrapping to a negative number.
     *
     * <p>Returns {@code totalPages == 0} when {@code totalItems <= 0}
     * or when {@code size <= 0} (the degenerate "unpaged" case), which
     * matches the original behaviour relied on by frontend pagination
     * controls.</p>
     */
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    /**
     * Empty-page constant builder for endpoints that need to return a
     * well-formed envelope without performing any database query (e.g.
     * a guarded path that detects "no entitlement" early). The
     * {@code totalPages} field is forced to {@code 0} per the contract
     * above.
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0L, 0);
    }

    /**
     * Overflow-safe computation of {@code ceil(totalItems / size)}. Uses
     * the {@code (totalItems + size - 1) / size} idiom in {@code long}
     * arithmetic and saturates at {@link Integer#MAX_VALUE} so a
     * pathological combination cannot wrap to a negative page count.
     */
    private static int computeTotalPages(long totalItems, int size) {
        if (totalItems <= 0L || size <= 0) {
            return 0;
        }
        long pages = (totalItems + size - 1L) / size;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
