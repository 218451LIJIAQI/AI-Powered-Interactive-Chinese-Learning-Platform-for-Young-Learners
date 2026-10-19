package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/** Right-hand option of a matching game, shuffled on every request and not linked to its item. */
public record StudentMatchingOptionResponse(String text, String imageUrl) {

    public StudentMatchingOptionResponse {
        Objects.requireNonNull(text, "text must not be null");
    }
}