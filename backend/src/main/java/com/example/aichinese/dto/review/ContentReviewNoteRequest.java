package com.example.aichinese.dto.review;

import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Size;

/** Optional admin note for approve / archive / restore decisions. */
@JsonPropertyOrder({ "note" })
public record ContentReviewNoteRequest(
        @Size(max = 1000, message = "Note must be 1000 characters or fewer.")
        String note) {

    public ContentReviewNoteRequest {
        note = stripToNull(note);
    }
}