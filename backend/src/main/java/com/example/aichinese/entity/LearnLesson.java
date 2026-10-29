package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * A stop on the Learn path: a lesson, a bonus chest, a unit review or a level checkpoint (the last stop of the last
 * unit of a level).
 */
@Getter
@Setter
@Entity
@Table(name = "learn_lessons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_learn_lessons_unit_position", columnNames = { "unit_id", "position" })
})
public class LearnLesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, updatable = false)
    private LearnUnit unit;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "lesson_kind", nullable = false, length = 20)
    private LearnLessonKind lessonKind = LearnLessonKind.LESSON;

    @Column(name = "minutes", nullable = false)
    private Integer minutes;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward;
}
