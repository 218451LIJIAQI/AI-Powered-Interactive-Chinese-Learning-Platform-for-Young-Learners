package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.UploadedFile;

/**
 * Persistence operations for {@link UploadedFile} \u2014 the platform's
 * abstract file pointer (storage key, original filename, content type,
 * size and uploader) that every upload-aware feature
 * (recorded videos, teacher verification documents, AI helper image
 * uploads, badge icons) joins to.
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} surface because every lookup goes through the
 * owning entity's foreign key reference rather than a direct
 * key-based read \u2014 there is no business workflow that searches the
 * uploaded-file table directly. Adding bespoke methods here would
 * over-engineer a passive blob-pointer table.</p>
 */
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
}
