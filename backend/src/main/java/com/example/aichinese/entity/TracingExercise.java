package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tracing_exercises", indexes = {
        @Index(name = "idx_tracing_exercises_lesson_id", columnList = "lesson_id")
})
public class TracingExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "worksheet_url", length = 255)
    private String worksheetUrl;

    @Column(name = "target_character", length = 100)
    private String targetCharacter;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;
}
