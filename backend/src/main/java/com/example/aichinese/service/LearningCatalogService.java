package com.example.aichinese.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;

/**
 * Read-only catalog lookups for the publicly visible learning taxonomy:
 * {@link LearningLevel}s (beginner / intermediate / advanced bands)
 * and {@link LearningCategory}s (vocabulary / grammar / culture etc.).
 *
 * <p>Exposed by unauthenticated catalog endpoints to populate filter
 * dropdowns in the public course-browser. Always sorted by
 * {@code sortOrder} then {@code name} so the front-end ordering is
 * deterministic and admins can control the rendering by editing
 * {@code sort_order}.</p>
 */
@Service
public class LearningCatalogService {

    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;

    public LearningCatalogService(
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository) {
        this.learningLevelRepository = Objects.requireNonNull(learningLevelRepository, "learningLevelRepository");
        this.learningCategoryRepository = Objects.requireNonNull(learningCategoryRepository, "learningCategoryRepository");
    }

    @Transactional(readOnly = true)
    public List<LearningLevelResponse> getPublicLevels() {
        return learningLevelRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toLevelResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LearningCategoryResponse> getPublicCategories() {
        return learningCategoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    private LearningLevelResponse toLevelResponse(LearningLevel level) {
        if (level == null) {
            return null;
        }

        return new LearningLevelResponse(level.getId(), level.getName(), level.getDescription());
    }

    private LearningCategoryResponse toCategoryResponse(LearningCategory category) {
        if (category == null) {
            return null;
        }

        return new LearningCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
