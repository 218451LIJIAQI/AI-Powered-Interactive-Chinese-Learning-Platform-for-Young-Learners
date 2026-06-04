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
