package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PracticeActivityContent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "listening_exercises", indexes = {
        @Index(name = "idx_listening_exercises_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_listening_exercises_hsk_level", columnList = "hsk_level")
})
public class ListeningExercise extends PracticeActivityContent {

    @Lob
    @Column(name = "question_payload", columnDefinition = "LONGTEXT")
    private String questionPayload;
}
