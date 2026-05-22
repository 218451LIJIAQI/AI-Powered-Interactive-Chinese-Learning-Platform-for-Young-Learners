package com.example.aichinese.util;

public final class PaginationUtils {

    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    public static int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    public static int sanitizeSize(int size) {
        return sanitizeSize(size, DEFAULT_MAX_PAGE_SIZE);
    }

    public static int sanitizeSize(int size, int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be positive.");
        }
        return Math.min(Math.max(size, 1), maxSize);
    }

    public static int startIndex(int page, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long offset = (long) sanitizePage(page) * sanitizeSize(size);
        return (int) Math.min(offset, totalItems);
    }

    public static int endIndex(int startIndex, int size, int totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        long end = (long) Math.max(startIndex, 0) + sanitizeSize(size);
        return (int) Math.min(end, totalItems);
    }

    public static int totalPages(long totalItems, int size) {
        if (totalItems <= 0) {
            return 0;
        }
        int safeSize = sanitizeSize(size);
        long pages = (totalItems + safeSize - 1L) / safeSize;
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }
}
