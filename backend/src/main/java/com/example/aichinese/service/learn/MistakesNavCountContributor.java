package com.example.aichinese.service.learn;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.navigation.NavCountContributor;

/** The student sidebar's Practice badge: mistakes waiting for review. */
@Component
public class MistakesNavCountContributor implements NavCountContributor {

    private final StudentProfileRepository studentProfileRepository;
    private final LearningMistakeRepository mistakeRepository;

    public MistakesNavCountContributor(
            StudentProfileRepository studentProfileRepository,
            LearningMistakeRepository mistakeRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.mistakeRepository = mistakeRepository;
    }

    @Override
    public RoleName role() {
        return RoleName.STUDENT;
    }

    @Override
    public Map<String, Long> counts(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .map(student -> Map.of("mistakes", mistakeRepository.countByStudentIdAndClearedAtIsNull(student.getId())))
                .orElse(Map.of());
    }
}
