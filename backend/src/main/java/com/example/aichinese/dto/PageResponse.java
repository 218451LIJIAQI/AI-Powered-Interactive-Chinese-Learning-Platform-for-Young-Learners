package com.example.aichinese.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One page of a list; {@code totalPages} is always derived from {@code totalItems} and {@code size}. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems) {

    public PageResponse {
        if (page < 0) {
            throw new IllegalStateException("Page must not be negative.");
        }
        if (size <= 0) {
            throw new IllegalStateException("Page size must be positive.");
        }
        if (totalItems < 0) {
            throw new IllegalStateException("Total items must not be negative.");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** The content of {@code source} mapped item by item, with its page, size and total. */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<? super E, ? extends T> mapper) {
        return new PageResponse<>(source.getContent().stream().<T>map(mapper).toList(), source.getNumber(),
                source.getSize(), source.getTotalElements());
    }

    @JsonProperty("totalPages")
    public long totalPages() {
        return totalItems == 0L ? 0 : (totalItems - 1L) / size + 1L;
    }
}
