package com.example.aichinese.service.classes;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.service.navigation.NavCountContributor;

/** The admin Content badge: classes, class details, lessons and lesson revisions waiting for review. */
@Component
public class ReviewQueueNavCountContributor implements NavCountContributor {

    private final ClassReviewService reviews;

    public ReviewQueueNavCountContributor(ClassReviewService reviews) {
        this.reviews = reviews;
    }

    @Override
    public RoleName role() {
        return RoleName.ADMIN;
    }

    @Override
    public Map<String, Long> counts(Long userId) {
        return Map.of("reviewQueue", reviews.waitingCount());
    }
}
