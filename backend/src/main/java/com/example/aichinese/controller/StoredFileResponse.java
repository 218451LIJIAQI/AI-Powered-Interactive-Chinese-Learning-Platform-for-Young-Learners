package com.example.aichinese.controller;

import static org.springframework.http.HttpHeaders.CONTENT_RANGE;
import static org.springframework.http.HttpHeaders.RANGE;
import static org.springframework.http.MediaType.ALL_VALUE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** The full and partial responses Spring MVC serves for a stored Resource. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        parameters = @Parameter(name = RANGE, in = ParameterIn.HEADER,
                description = "Optional byte ranges, for example bytes=0-1023.", schema = @Schema(type = "string")),
        responses = {
                @ApiResponse(responseCode = "200", description = "The full file in its media type.",
                        content = @Content(mediaType = ALL_VALUE, schema = @Schema(type = "string", format = "binary"))),
                @ApiResponse(responseCode = "206", description = "One byte range or a multipart body with several ranges.",
                        headers = @Header(name = CONTENT_RANGE, description = "Present for a single range.",
                                schema = @Schema(type = "string")),
                        content = {
                                @Content(mediaType = ALL_VALUE, schema = @Schema(type = "string", format = "binary")),
                                @Content(mediaType = "multipart/byteranges",
                                        schema = @Schema(type = "string", format = "binary"))
                        }),
                @ApiResponse(responseCode = "416", description = "The requested range cannot be satisfied.",
                        headers = @Header(name = CONTENT_RANGE, description = "The available file length.",
                                schema = @Schema(type = "string")),
                        content = @Content)
        })
@interface StoredFileResponse {
}
