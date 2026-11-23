package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "uploaded_files", uniqueConstraints = {
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

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
