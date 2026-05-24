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
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_file_name", nullable = false, unique = true, length = 255)
    private String storedFileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "access_level", nullable = false, length = 30)
    private String accessLevel;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
