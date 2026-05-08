package com.example.aichinese.entity;

import java.math.BigDecimal;

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
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_reviewer_id", columnList = "reviewer_id"),
        @Index(name = "idx_reviews_target_type_target_id", columnList = "target_type,target_id")
})
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private StudentProfile reviewer;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @Lob
    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;
}
