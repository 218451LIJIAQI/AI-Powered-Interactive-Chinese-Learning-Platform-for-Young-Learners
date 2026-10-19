package com.example.aichinese.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "items", "page", "size", "totalItems", "totalPages" })
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        page = Math.max(page, 0);
        size = Math.max(size, 0);
        totalItems = Math.max(totalItems, 0L);
        totalPages = computeTotalPages(totalItems, size);
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, 0);
    }

    private static int computeTotalPages(long totalItems, int size) {
        if (totalItems <= 0L || size <= 0) {
            return 0;
        }
        long pages = ((totalItems - 1L) / size) + 1L;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
