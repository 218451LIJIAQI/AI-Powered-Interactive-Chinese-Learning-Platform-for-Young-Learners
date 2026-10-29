package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PracticeActivityContent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "drag_drop_activities", indexes = {
        @Index(name = "idx_drag_drop_activities_status", columnList = "content_status"),
        @Index(name = "idx_drag_drop_activities_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_drag_drop_activities_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_drag_drop_activities_difficulty", columnList = "difficulty"),
        @Index(name = "idx_drag_drop_activities_created_by_user_id", columnList = "created_by_user_id")
})
public class DragDropActivity extends PracticeActivityContent {

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 15;
}
