package com.example.aichinese.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.service.classes.ClassCatalogueService;

import lombok.RequiredArgsConstructor;

/** The public class catalogue: anyone can browse classes and preview their units and lessons. */
@RestController
@RequestMapping("/api/v1/public/classes")
@RequiredArgsConstructor
public class PublicClassController {

    private final ClassCatalogueService catalogue;

    @GetMapping
    public ApiResponse<PageResponse<ClassViews.ClassCard>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.success("Classes retrieved successfully.", catalogue.publicClasses(page, size));
    }

    @GetMapping("/{classId:\\d+}")
    public ApiResponse<ClassViews.ClassPreview> preview(@PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", catalogue.preview(classId));
    }
}
