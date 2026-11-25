package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.PracticeActivityContent;

/**
 * A pinyin typing set. Its words live in a JSON payload:
 * {@code {"words":[{"meaning":"Thank you","hanzi":"谢谢","pinyin":"xièxie","decoys":"写写 歇歇 谢 写"}]}}. The
 * decoys are other words spelt with the same letters, offered next to the answer as typing candidates.
 */
@Getter
@Setter
@Entity
@Table(name = "typing_exercises", indexes = {
        @Index(name = "idx_typing_exercises_status_updated", columnList = "content_status,updated_at")
})
public class TypingExercise extends PracticeActivityContent {

    @Lob
    @Column(name = "word_payload", columnDefinition = "LONGTEXT")
    private String wordPayload;
}
