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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matching_game_items", indexes = {
        @Index(name = "idx_matching_game_items_game_id", columnList = "matching_game_id")
})
public class MatchingGameItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matching_game_id", nullable = false)
    private MatchingGame matchingGame;

    @Column(name = "left_text", nullable = false, length = 500)
    private String leftText;

    @Column(name = "right_text", nullable = false, length = 500)
    private String rightText;

    @Column(name = "left_image_url", length = 255)
    private String leftImageUrl;

    @Column(name = "right_image_url", length = 255)
    private String rightImageUrl;

    @Column(name = "pinyin", length = 255)
    private String pinyin;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
