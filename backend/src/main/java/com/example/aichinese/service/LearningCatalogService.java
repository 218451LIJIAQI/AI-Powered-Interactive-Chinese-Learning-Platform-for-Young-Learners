package com.example.aichinese.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
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

    @Transactional(readOnly = true)
    public List<LearningLevelResponse> getAdminLevels() {
        return getPublicLevels();
    }

    @Transactional(readOnly = true)
    public List<LearningCategoryResponse> getAdminCategories() {
        return getPublicCategories();
    }

    public LearningLevelResponse toLevelResponse(LearningLevel level) {
        if (level == null) {
            return null;
        }

        return new LearningLevelResponse(level.getId(), level.getName(), level.getDescription());
    }

    public LearningCategoryResponse toCategoryResponse(LearningCategory category) {
        if (category == null) {
            return null;
        }

        return new LearningCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
