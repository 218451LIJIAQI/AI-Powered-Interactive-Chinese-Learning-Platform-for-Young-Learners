package com.example.aichinese.repository.projection;

/**
 * Spring Data JPA projection used by the {@code GROUP BY status} queries on
 * the payment / AI usage / content repositories.
 *
 * <p>Lets the admin analytics service replace the previous pattern of
 * {@code repo.findAll().stream().collect(groupingBy(status, counting()))} —
 * which materialised entire tables in JVM memory just to compute a small
 * breakdown — with a single SQL {@code GROUP BY} that the database can
 * answer using existing status-column indexes.</p>
 *
 * <p>The status field is exposed as {@link String} (rather than the entity's
 * enum type) so a single projection can be shared by every repository
 * regardless of which enum the table uses.</p>
 */
public interface StatusCountProjection {

    String getStatus();

    long getCount();
}
