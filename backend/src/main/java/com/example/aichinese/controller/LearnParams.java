package com.example.aichinese.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;

/** Path and query parameters of the Learn, Practice and Progress endpoints. */
final class LearnParams {

    private LearnParams() {
    }

    /** A Practice type slug ({@code flashcards}, {@code drag-drop} ...); an unknown slug is not a page. */
    static PracticeType practiceType(String slug) {
        return PracticeType.tryFrom(slug)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Unknown practice type."));
    }

    /** Optional filter: blank means every type. */
    static PracticeType optionalPracticeType(String value) {
        return value == null || value.isBlank() ? null : practiceType(value);
    }

    static ContentStatus optionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ContentStatus.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown status: " + value));
    }

    static LearningStage optionalStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LearningStage.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown stage.");
        }
    }

    /** {@code all} (or blank) means both sources. */
    static MistakeSource optionalMistakeSource(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value.strip())) {
            return null;
        }
        return MistakeSource.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown mistake source."));
    }

    static YearMonth optionalMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Month must look like 2026-05.");
        }
    }
}
