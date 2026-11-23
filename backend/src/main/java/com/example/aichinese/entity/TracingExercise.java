package com.example.aichinese.entity;

import com.example.aichinese.entity.base.LessonActivity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tracing_exercises", indexes = {
        @Index(name = "idx_tracing_exercises_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_tracing_exercises_hsk_level", columnList = "hsk_level")
})
public class TracingExercise extends LessonActivity {

    @Column(name = "target_character", length = 100)
    private String targetCharacter;

    /**
     * One value per character, joined with "|" for a class tracing activity (TracingCharacters): its 8 characters
     * with up to 40 characters of pinyin and 80 of meaning each must fit.
     */
    @Column(name = "pinyin", length = 400)
    private String pinyin;

    @Column(name = "meaning", length = 700)
    private String meaning;

    /** Strokes of all target characters together; the pad asks for this many before Done. */
    @Column(name = "stroke_count")
    private Integer strokeCount;

    /**
     * The strokes of each target character, comma-separated ("4,4" for 中文), adding up to {@code strokeCount}; null
     * when the set does not say (a single character has its {@code strokeCount}).
     */
    @Column(name = "character_strokes", length = 100)
    private String characterStrokes;
}
