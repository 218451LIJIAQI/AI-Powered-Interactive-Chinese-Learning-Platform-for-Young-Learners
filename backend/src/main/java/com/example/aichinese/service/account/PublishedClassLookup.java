package com.example.aichinese.service.account;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.PublishedClassSummary;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.persistence.EntityManager;

/** A teacher's published classes (the teaching profile, account deletion and the Deactivate dialog list them). */
@Component
public class PublishedClassLookup {

    private final EntityManager entityManager;

    public PublishedClassLookup(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PublishedClassSummary> publishedClasses(User teacher) {
        return entityManager.createQuery("""
                        select p.id, p.title from CoursePackage p
                        where p.teacher.user.id = :userId and p.contentStatus = :published
                        order by p.title
                        """, Object[].class)
                .setParameter("userId", teacher.getId())
                .setParameter("published", ContentStatus.PUBLISHED)
                .getResultList()
                .stream()
                .map(row -> new PublishedClassSummary((Long) row[0], (String) row[1]))
                .toList();
    }
}
