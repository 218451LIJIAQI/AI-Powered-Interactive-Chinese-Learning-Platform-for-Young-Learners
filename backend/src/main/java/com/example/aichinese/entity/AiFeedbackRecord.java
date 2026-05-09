package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_feedback_records", indexes = {
        @Index(name = "idx_ai_feedback_records_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_feedback_records_feature_key", columnList = "feature_key")
})
public class AiFeedbackRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "input_file_url", length = 255)
    private String inputFileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_file_id")
    private UploadedFile uploadedFile;

    @Lob
    @Column(name = "input_text")
    private String inputText;

    @Lob
    @Column(name = "feedback_text")
    private String feedbackText;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "status", length = 50)
    private String status;
}
