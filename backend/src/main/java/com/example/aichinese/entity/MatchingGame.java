package com.example.aichinese.entity;

import com.example.aichinese.entity.base.LessonActivity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "matching_games", indexes = {
        @Index(name = "idx_matching_games_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_matching_games_hsk_level", columnList = "hsk_level")
})
public class MatchingGame extends LessonActivity {
}
