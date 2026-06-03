package com.example.aichinese.repository.projection;

import org.springframework.lang.Nullable;

/**
 * Spring Data JPA interface projection used by the {@code GROUP BY status}
 * queries on the payment / AI-usage / content repositories.
 *
 * <p>Lets the admin analytics service replace the previous pattern of
 * {@code repo.findAll().stream().collect(groupingBy(status, counting()))} —
 * which materialised entire tables into JVM memory just to compute a small
 * breakdown — with a single SQL {@code GROUP BY} that the database can
 * answer using the existing status-column indexes
 * ({@code idx_*_status}). The reduction is from O(rows) JVM work + heap
 * pressure to O(distinct statuses) network bytes.</p>
 *
 * <h2>Why {@link String} instead of the underlying enum type</h2>
 * <p>The status column is exposed as {@code String} (rather than each
 * entity's specific {@code ContentStatus} / {@code PaymentStatus} /
 * {@code AiResponseReportStatus} enum) so that a single projection
 * interface can back every {@code GROUP BY} query across the platform.
 * Hibernate emits the column value as the enum's
 * {@link Enum#name() name()} when the column is mapped
 * {@code @Enumerated(EnumType.STRING)} (which every status column in this
 * platform is — see entity definitions). Callers should prefer
 * {@code projection.getStatus()} and normalise it according to the
 * response contract for that particular breakdown.</p>
 *
 * <h2>Null &amp; wire-format contract</h2>
 * <ul>
 *   <li>{@link #getStatus()} reflects the database value verbatim. For
 *       {@code @Enumerated(EnumType.STRING)} columns marked
 *       {@code nullable = false} (the case for every status column
 *       producing these projections today) the value is non-{@code null};
 *       the return type is left annotated {@link Nullable} so the same
 *       projection can be safely reused by future outer-join queries that
 *       may legitimately produce a {@code null} group key.</li>
 *   <li>{@link #getCount()} returns the {@code COUNT(*)} aggregate, which
 *       SQL guarantees to be non-negative; the {@code long} primitive is
 *       safe here because {@code COUNT(*)} never returns {@code NULL}.</li>
 * </ul>
 *
 * <p>The interface lives in the {@code repository.projection} sub-package
 * (rather than {@code dto}) because it is a persistence-layer concern:
 * Spring Data JPA materialises rows directly into a dynamic proxy that
 * implements this interface — there is no separate DTO mapping step.
 * The interface deliberately stays projection-only: conversion and
 * response-shaping logic belong in the service layer that owns the
 * resulting analytics contract.</p>
 */
public interface StatusCountProjection {

    /**
     * @return the enum {@link Enum#name() name()} string of the grouped
     *         status; non-{@code null} for non-nullable status columns,
     *         but callers should still handle {@code null} defensively
     *         to keep the projection reusable for outer-join queries
     */
    @Nullable
    String getStatus();

    /**
     * @return number of rows matching the grouped status; always
     *         {@code >= 0}
     */
    long getCount();

}
