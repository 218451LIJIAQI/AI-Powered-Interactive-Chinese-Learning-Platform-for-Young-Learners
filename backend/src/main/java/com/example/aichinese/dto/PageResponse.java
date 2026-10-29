package com.example.aichinese.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.example.aichinese.util.PaginationUtils;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One page of a list; {@code totalPages} is always derived from {@code totalItems} and {@code size}. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        page = Math.max(page, 0);
        size = Math.max(size, 0);
        totalItems = Math.max(totalItems, 0L);
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems);
    }

    /** The content of {@code source} mapped item by item, with its page, size and total. */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<? super E, ? extends T> mapper) {
        return of(source.getContent().stream().<T>map(mapper).toList(), source.getNumber(), source.getSize(),
                source.getTotalElements());
    }

    /**
     * The requested page of a list held in memory (page and size sanitized like every paged query), its items
     * converted together so a page can load what it shows in one batch.
     */
    public static <E, T> PageResponse<T> fromList(List<E> all, int page, int size,
            Function<? super List<E>, ? extends List<T>> convert) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        int start = PaginationUtils.startIndex(safePage, safeSize, all.size());
        int end = PaginationUtils.endIndex(start, safeSize, all.size());
        return of(convert.apply(all.subList(start, end)), safePage, safeSize, all.size());
    }

    @JsonProperty("totalPages")
    public int totalPages() {
        if (totalItems <= 0L || size <= 0) {
            return 0;
        }
        long pages = ((totalItems - 1L) / size) + 1L;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
