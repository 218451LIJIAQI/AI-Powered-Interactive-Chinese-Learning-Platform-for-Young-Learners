package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        @Index(name = "idx_teacher_verification_documents_uploaded_file_id", columnList = "uploaded_file_id")
})
public class TeacherVerificationDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_file_id", nullable = false)
    private UploadedFile uploadedFile;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "document_note", length = 500)
    private String documentNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "current_version", nullable = false)
    private boolean currentVersion = true;
}
