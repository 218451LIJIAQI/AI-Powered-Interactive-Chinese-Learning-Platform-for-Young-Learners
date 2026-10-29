package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.PracticeActivityContent;

/** A Practice "Quizzes" set: multiple-choice questions with instant feedback (platform content, admin-owned). */
@Getter
@Setter
@Entity
@Table(name = "practice_quizzes", indexes = {
        @Index(name = "idx_practice_quizzes_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_practice_quizzes_created_by_user_id", columnList = "created_by_user_id")
})
public class PracticeQuiz extends PracticeActivityContent {

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 15;
}
