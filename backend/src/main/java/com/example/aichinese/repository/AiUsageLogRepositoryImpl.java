package com.example.aichinese.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Dynamic criteria queries for AI usage logs so absent filters are omitted from SQL instead of using
 * {@code (:param is null or col = :param)}, letting MySQL pick selective indexes.
 */
class AiUsageLogRepositoryImpl implements AiUsageLogRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<AdminAiUsageLogResponse> findAdminPage(
            AiFeatureKey feature, AiRequestStatus status, String role, Pageable page) {
        long total = countMatching(feature, status, role);
        if (total == 0 || (page.isPaged() && page.getOffset() >= total)) {
            return new PageImpl<>(List.of(), page, total);
        }
        List<AdminAiUsageLogResponse> rows = selectRows(feature, status, role, page);
        return new PageImpl<>(rows, page, total);
    }

    @Override
    public List<AdminAiUsageLogResponse> findExportRows(
            AiFeatureKey feature, AiRequestStatus status, String role, Pageable limit) {
        return selectRows(feature, status, role, limit);
    }

    private long countMatching(AiFeatureKey feature, AiRequestStatus status, String role) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<AiUsageLog> root = query.from(AiUsageLog.class);
        Predicate[] predicates = predicates(cb, root, feature, status, role);
        query.select(cb.count(root));
        if (predicates.length > 0) {
            query.where(predicates);
        }
        return entityManager.createQuery(query).getSingleResult();
    }

    private List<AdminAiUsageLogResponse> selectRows(
            AiFeatureKey feature, AiRequestStatus status, String role, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminAiUsageLogResponse> query = cb.createQuery(AdminAiUsageLogResponse.class);
        Root<AiUsageLog> root = query.from(AiUsageLog.class);
        Join<AiUsageLog, User> user = root.join("user", JoinType.LEFT);
        query.select(cb.construct(
                AdminAiUsageLogResponse.class,
                root.get("id"),
                cb.coalesce(user.get("displayName"), ""),
                root.get("userRole"),
                root.get("featureKey"),
                root.get("status"),
                root.get("errorSummary"),
                root.get("createdAt")));
        Predicate[] predicates = predicates(cb, root, feature, status, role);
        if (predicates.length > 0) {
            query.where(predicates);
        }
        query.orderBy(orders(cb, root, pageable.getSort()));
        TypedQuery<AdminAiUsageLogResponse> typed = entityManager.createQuery(query);
        if (pageable.isPaged()) {
            typed.setFirstResult((int) pageable.getOffset());
            typed.setMaxResults(pageable.getPageSize());
        }
        return typed.getResultList();
    }

    private static Predicate[] predicates(
            CriteriaBuilder cb, Root<AiUsageLog> root, AiFeatureKey feature, AiRequestStatus status, String role) {
        List<Predicate> list = new ArrayList<>(3);
        if (feature != null) {
            list.add(cb.equal(root.get("featureKey"), feature));
        }
        if (status != null) {
            list.add(cb.equal(root.get("status"), status));
        }
        if (role != null) {
            list.add(cb.equal(root.get("userRole"), role));
        }
        return list.toArray(Predicate[]::new);
    }

    private static List<Order> orders(CriteriaBuilder cb, Root<AiUsageLog> root, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        }
        List<Order> list = new ArrayList<>();
        for (Sort.Order order : sort) {
            var path = root.get(order.getProperty());
            list.add(order.isAscending() ? cb.asc(path) : cb.desc(path));
        }
        return list;
    }
}
