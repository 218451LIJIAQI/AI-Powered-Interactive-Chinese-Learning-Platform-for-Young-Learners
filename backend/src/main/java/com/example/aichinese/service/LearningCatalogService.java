package com.example.aichinese.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;

@Service
public class LearningCatalogService {

    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;

    public LearningCatalogService(
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository) {
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_LEVELS)
    public List<LearningLevelResponse> getPublicLevels() {
        return learningLevelRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(level -> new LearningLevelResponse(level.getId(), level.getName(), level.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_CATEGORIES)
    public List<LearningCategoryResponse> getPublicCategories() {
        return learningCategoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(category -> new LearningCategoryResponse(category.getId(), category.getName(), category.getDescription()))
                .toList();
    }
}
