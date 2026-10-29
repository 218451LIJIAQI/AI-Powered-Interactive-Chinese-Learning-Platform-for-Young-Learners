package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;

/**
 * A multiple-choice question of the level check that places new students on the path. Admin-owned; only published
 * questions are asked. The counts are placement statistics shown to admins (no XP is involved).
 */
@Getter
@Setter
@Entity
@Table(name = "level_check_questions", indexes = {
        @Index(name = "idx_level_check_questions_pick", columnList = "publish_status,mandarin_level,difficulty")
})
public class LevelCheckQuestion extends BaseEntity {

    @Column(name = "prompt", nullable = false, length = 300)
    private String prompt;

    @Column(name = "hanzi", length = 100)
    private String hanzi;

    @Column(name = "pinyin", length = 200)
    private String pinyin;

    /** Read aloud by speech synthesis for listening questions. */
    @Column(name = "audio_text", length = 200)
    private String audioText;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false, length = 20)
    private LevelCheckSkill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private ActivityDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "mandarin_level", nullable = false, length = 20)
    private LevelCheckLevel mandarinLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 20)
    private PublishStatus publishStatus = PublishStatus.DRAFT;

    @Column(name = "answered_count", nullable = false)
    private Integer answeredCount = 0;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;
}
