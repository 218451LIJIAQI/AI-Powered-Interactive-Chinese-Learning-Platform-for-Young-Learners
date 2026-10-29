package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One message in a support request's thread, from the requester or from support, with an optional screenshot. */
@Getter
@Setter
@Entity
@Table(name = "support_messages", indexes = {
        @Index(name = "idx_support_messages_request_created", columnList = "request_id,created_at")
})
public class SupportMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private SupportRequest request;

    /** The writer; null for a visitor's message (and after the writer deleted the account). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(name = "from_support", nullable = false, updatable = false)
    private boolean fromSupport;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_file_id")
    private UploadedFile attachment;
}
