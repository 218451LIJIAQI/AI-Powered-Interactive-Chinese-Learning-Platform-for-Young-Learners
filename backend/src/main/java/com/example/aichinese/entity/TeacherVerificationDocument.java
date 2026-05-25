package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single document a {@link TeacherProfile} has submitted as evidence
 * during the verification flow.
 *
 * <p>{@link #documentType} categorises the upload (certificate,
 * qualification, identity, other; see
 * {@link com.example.aichinese.entity.enums.TeacherVerificationDocumentType}).
 * The actual file bytes live in the linked {@link UploadedFile} —
 * this entity is the verification-specific metadata wrapper.</p>
 *
 * <p>{@link #currentVersion} marks rows that are part of the latest
 * submission; superseded documents from prior verification rounds
 * are preserved (for audit) but flipped to {@code false}. The
 * generated column {@link #currentDocumentKey} (populated by the
 * database to a non-null value only when {@code currentVersion} is
 * true) backs the index that the admin tooling uses to fetch "latest
 * round per teacher" without scanning history.</p>
 *
 * <p>The associated {@link TeacherVerificationReview} captures the
 * admin decision on the submission as a whole.</p>
 *
 * <h3>Field mutability contract</h3>
 * <ul>
 *   <li><b>Immutable</b>: {@link #teacher}, {@link #uploadedFile},
 *       {@link #documentType}, {@link #documentNote},
 *       {@link #submittedAt} — the document's identity (who submitted
 *       which file, under which category, with what note, at what
 *       instant) is fixed at submission time. Editing any of these
 *       after the fact would corrupt the audit trail; instead the
 *       teacher uploads a new document and the prior round is
 *       superseded.</li>
 *   <li><b>Mutable</b>: {@link #currentVersion} — flipped to
 *       {@code false} by the supersession path in
 *       {@code TeacherManagementService} when a fresh document is
 *       uploaded for the same {@link TeacherProfile}.</li>
 *   <li><b>DB-generated, read-only</b>: {@link #currentDocumentKey}
 *       (already mapped {@code insertable = false, updatable = false}).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_verification_documents", indexes = {
        @Index(name = "idx_teacher_verification_documents_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_verification_documents_uploaded_file_id", columnList = "uploaded_file_id"),
        @Index(name = "idx_teacher_verification_documents_teacher_current_submitted", columnList = "teacher_id,current_version,submitted_at,id"),
        @Index(name = "idx_teacher_verification_documents_latest", columnList = "teacher_id,submitted_at,id")
})
public class TeacherVerificationDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_file_id", nullable = false, updatable = false)
    private UploadedFile uploadedFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 100)
    private TeacherVerificationDocumentType documentType;

    @Column(name = "document_note", updatable = false, length = 500)
    private String documentNote;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "current_version", nullable = false)
    private boolean currentVersion = true;

    @Column(name = "current_document_key", insertable = false, updatable = false, length = 160)
    private String currentDocumentKey;
}
