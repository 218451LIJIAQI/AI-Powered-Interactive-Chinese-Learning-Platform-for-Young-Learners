package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.UploadedFile;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    /**
     * A working copy can share its uploaded file with the published video. Keep all surviving references.
     * Intentionally omits {@code clearAutomatically} so callers in larger authoring transactions keep their managed
     * entities; {@code FileStorageService.deleteUnusedRecordedVideo} detaches the deleted {@code UploadedFile} explicitly.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from UploadedFile f where f.id = :id
              and not exists (select v.id from RecordedLessonVideo v where v.uploadedFile.id = :id)
              and not exists (select m.id from SupportMessage m where m.attachment.id = :id)
            """)
    int deleteIfUnreferenced(@Param("id") Long id);
}
