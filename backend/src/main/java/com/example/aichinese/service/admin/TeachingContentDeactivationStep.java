package com.example.aichinese.service.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.ReviewableContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.service.account.PublishedClassLookup;

import jakarta.persistence.EntityManager;

/**
 * A deactivated teacher's items waiting in the review queue go back to Draft (nobody reviews work whose author cannot
 * sign in). Published classes stay open to their students; the dialog lists them so the admin can archive them
 * separately.
 */
@Component
class TeachingContentDeactivationStep implements AccountDeactivationStep {

    /** The teacher content that can wait for review: classes and lessons (lesson revisions included). */
    private static final List<String> REVIEWABLE_ENTITIES = List.of("CoursePackage", "Lesson");

    private final EntityManager entityManager;
    private final PublishedClassLookup publishedClassLookup;

    TeachingContentDeactivationStep(EntityManager entityManager, PublishedClassLookup publishedClassLookup) {
        this.entityManager = entityManager;
        this.publishedClassLookup = publishedClassLookup;
    }

    @Override
    public void preview(User user, DeactivationTally impact) {
        publishedClassLookup.publishedClasses(user).forEach(published -> impact.addPublishedClass(published.title()));
        impact.addReviewItems(submittedItems(user).size());
    }

    @Override
    public void apply(User user, User admin, DeactivationTally done) {
        List<ReviewableContent> submitted = submittedItems(user);
        submitted.forEach(item -> item.setContentStatus(ContentStatus.DRAFT));
        done.addReviewItems(submitted.size());
    }

    private List<ReviewableContent> submittedItems(User user) {
        List<ReviewableContent> items = new ArrayList<>();
        for (String entity : REVIEWABLE_ENTITIES) {
            items.addAll(entityManager.createQuery(
                            "select e from " + entity + " e where e.teacher.user.id = :userId"
                                    + " and e.contentStatus = :submitted",
                            ReviewableContent.class)
                    .setParameter("userId", user.getId())
                    .setParameter("submitted", ContentStatus.SUBMITTED)
                    .getResultList());
        }
        return items;
    }
}
