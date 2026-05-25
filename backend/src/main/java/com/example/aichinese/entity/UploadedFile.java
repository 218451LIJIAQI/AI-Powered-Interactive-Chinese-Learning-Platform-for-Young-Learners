package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata row for any file the platform stores on its own object
 * storage — verification documents, recorded lesson videos, AI-feedback
 * audio uploads, etc.
 *
 * <p>{@link #fileName} is the original name as supplied by the
 * uploader (kept for download UX) while {@link #storedFileName} is the
 * platform-assigned UUID-based name used as the storage key. The
 * unique constraint on {@code stored_file_name} prevents key
 * collisions and is the natural primary lookup index for file-serving
 * endpoints.</p>
 *
 * <p>{@link #accessLevel} drives the file-serving authorisation
 * (currently free-form short codes like {@code "PUBLIC"},
 * {@code "TEACHER_VERIFICATION"}, {@code "STUDENT_AI_INPUT"}). Values
 * are interpreted by {@code FileStorageService} when generating signed
 * URLs.</p>
 *
 * <p>{@link #active} provides soft-deletion: serving the file checks
 * the flag in addition to the ACL, so a takedown can be staged before
 * the bytes are physically purged.</p>
 *
 * <p>{@link #ownerUser} is nullable because system-uploaded files
 * (seed data, automated exports) have no human owner.</p>
 *
 * <h3>Field mutability contract</h3>
 *
 * <p>Once an upload lands on object storage, the bytes-and-metadata
 * pairing is fundamentally immutable: rewriting the metadata in place
 * would either orphan the storage key or contradict references already
 * captured by downstream entities ({@link TeacherVerificationDocument},
 * {@link RecordedLessonVideo}, AI feedback turns).</p>
 *
 * <ul>
 *   <li><b>Immutable</b>: {@link #ownerUser}, {@link #fileName},
 *       {@link #storedFileName}, {@link #filePath},
 *       {@link #contentType}, {@link #fileSize},
 *       {@link #accessLevel}. The single legitimate write path is at
 *       upload time inside {@code FileStorageService},
 *       {@code AiFileStorageService} or
 *       {@code RecordedVideoStorageService}.</li>
 *   <li><b>Mutable</b>: {@link #active} only — reserved for the
 *       documented soft-delete flow above; flipping the flag stages a
 *       takedown without erasing the metadata row.</li>
 * </ul>
 *
 * <p>Uniqueness of {@link #storedFileName} is enforced by the single
 * named {@link UniqueConstraint} on the {@link Table @Table}
 * annotation; the historical {@code @Column(unique = true)} duplicate
 * was removed to avoid Hibernate emitting an extra unnamed unique
 * declaration that the Flyway baseline does not own.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "uploaded_files", indexes = {
        @Index(name = "idx_uploaded_files_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "idx_uploaded_files_created_at", columnList = "created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_uploaded_files_stored_file_name", columnNames = { "stored_file_name" })
})
public class UploadedFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", updatable = false)
    private User ownerUser;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "stored_file_name", nullable = false, updatable = false, length = 255)
    private String storedFileName;

    @Column(name = "file_path", nullable = false, updatable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    @Column(name = "access_level", nullable = false, updatable = false, length = 30)
    private String accessLevel;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
