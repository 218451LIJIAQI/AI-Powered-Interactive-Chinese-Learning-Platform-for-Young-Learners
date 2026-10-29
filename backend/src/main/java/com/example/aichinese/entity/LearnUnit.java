package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * One unit of the platform's own Learn path (Unit 1 "Say hello · 你好" ...). Units are numbered across the whole
 * path ({@code position}) and grouped into path levels (1 = Beginner 1 / HSK 1, 2 = Beginner 2 / HSK 2). Only admins
 * change them; a draft unit is hidden from students.
 */
@Getter
@Setter
@Entity
@Table(name = "learn_units", indexes = {
        @Index(name = "idx_learn_units_status", columnList = "publish_status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_learn_units_position", columnNames = { "position" })
})
public class LearnUnit extends BaseEntity {

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "path_level", nullable = false)
    private Integer pathLevel;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "title_zh", nullable = false, length = 40)
    private String titleZh;

    @Column(name = "hsk_level", nullable = false)
    private Integer hskLevel;

    /** IconGlyph name of the unit badge (hand, utensils, map ...). */
    @Column(name = "icon_key", nullable = false, length = 40)
    private String iconKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 20)
    private PublishStatus publishStatus = PublishStatus.DRAFT;

    /** Guidebook tips, one per line. */
    @Lob
    @Column(name = "guidebook_tips", columnDefinition = "TEXT")
    private String guidebookTips;
}
