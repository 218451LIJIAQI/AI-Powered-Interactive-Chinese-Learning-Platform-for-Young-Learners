package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.ContentModerationRecord;

/**
 * Persistence operations for {@link ContentModerationRecord} — the
 * append-only ledger of admin moderation actions taken against
 * teacher-authored content (lessons, packages, classes), teacher
 * profiles, and student-flagged messages.
 *
 * <p>Every read eager-fetches {@code adminUser}, {@code relatedTeacher},
 * and {@code relatedTeacher.user} so the admin moderation overview
 * renders "who moderated which teacher's content" in a single round
 * trip, without N+1 lazy lookups during response mapping.</p>
 */
public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {

    /**
     * Eager-fetch override of the inherited {@link JpaRepository#findAll()}
     * — used by integration tests that assert specific moderation rows
     * were emitted (e.g. that an archive action produced an
     * {@code ARCHIVE} record). Production code paths page through
     * {@link #findAll(Pageable)} rather than this unbounded variant.
     */
    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    List<ContentModerationRecord> findAll();

    /**
     * Admin moderation-overview page. Pagination + ordering happen
     * entirely in SQL so the query stays O(page&nbsp;size) regardless of
     * total record count, which matters because the moderation log
     * grows on every admin action across the platform.
     */
    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    Page<ContentModerationRecord> findAll(@NonNull Pageable pageable);
}
