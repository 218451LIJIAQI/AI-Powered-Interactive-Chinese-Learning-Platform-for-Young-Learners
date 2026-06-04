package com.example.aichinese.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;

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
    @Cacheable(cacheNames = CacheConfig.PUBLIC_LEVELS)
    public List<LearningLevelResponse> getPublicLevels() {
        return learningLevelRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toLevelResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_CATEGORIES)
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
