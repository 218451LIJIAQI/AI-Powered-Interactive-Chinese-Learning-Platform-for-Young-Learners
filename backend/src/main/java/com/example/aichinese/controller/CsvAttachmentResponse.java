package com.example.aichinese.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Shared OpenAPI 200 response contract for UTF-8 CSV download endpoints. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "200",
        description = "UTF-8 CSV attachment with a byte-order mark.",
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
@interface CsvAttachmentResponse {
}
