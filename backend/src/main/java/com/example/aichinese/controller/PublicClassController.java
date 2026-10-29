package com.example.aichinese.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.service.classes.ClassCatalogueService;

/** The public class catalogue: anyone can browse classes and preview their units and lessons. */
@RestController
@RequestMapping("/api/v1/public/classes")
public class PublicClassController {

    private final ClassCatalogueService catalogue;

    public PublicClassController(ClassCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    public ApiResponse<PageResponse<ClassViews.ClassCard>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "levelId", required = false) Long levelId,
            @RequestParam(value = "learningType", required = false) String learningType,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.success("Classes retrieved successfully.", catalogue.search(
                new ClassCatalogueService.Query(keyword, topicId, levelId, learningType, sort), Set.of(), page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<ClassViews.CatalogueSummary> summary() {
        return ApiResponse.success("Class catalogue summary retrieved successfully.", catalogue.summary());
    }

    @GetMapping("/featured")
    public ApiResponse<List<ClassViews.ClassCard>> featured() {
        return ApiResponse.success("Featured classes retrieved successfully.", catalogue.featured());
    }

    @GetMapping("/{classId:\\d+}")
    public ApiResponse<ClassViews.ClassPreview> preview(@PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", catalogue.preview(classId));
    }
}
