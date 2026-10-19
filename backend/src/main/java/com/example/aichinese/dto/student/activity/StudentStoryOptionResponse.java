package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/** One answer option of a story question (no correct flag). */
public record StudentStoryOptionResponse(int index, String text) {

    public StudentStoryOptionResponse {
        Objects.requireNonNull(text, "text must not be null");
    }
}