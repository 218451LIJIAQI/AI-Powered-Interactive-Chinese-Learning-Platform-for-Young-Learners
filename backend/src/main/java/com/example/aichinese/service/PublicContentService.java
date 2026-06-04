package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.PublicCourseDetailResponse;
import com.example.aichinese.dto.content.PublicCourseSummaryResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
public class PublicContentService {

    private static final int PUBLIC_PACKAGE_ITEM_LIMIT = 100;

    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final ContentManagementMapper contentManagementMapper;
    private final CommerceSettingsService commerceSettingsService;
    private final EntityManager entityManager;

    public PublicContentService(
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            ContentManagementMapper contentManagementMapper,
            CommerceSettingsService commerceSettingsService,
            EntityManager entityManager) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicCourseSummaryResponse> discoverCourses(
            String keyword,
            Long levelId,
            Long categoryId,
            String accessType,
            String learningType,
            String sort,
            int page,
            int size) {
        AccessType accessTypeFilter = parseAccessType(accessType);
        LearningType learningTypeFilter = parseLearningType(learningType);
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);

        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        String currencyCode = commerceSettingsService.getDefaultCurrency();
        long totalItems = countPublicCourses(levelId, categoryId, accessTypeFilter, learningTypeFilter, normalizedKeyword);
        long offset = (long) safePage * safeSize;
        List<PublicCourseSummaryResponse> items = offset >= totalItems
                ? List.of()
                : loadCourseSummaries(findPublicCourseKeys(
                        levelId,
                        categoryId,
                        accessTypeFilter,
                        learningTypeFilter,
                        normalizedKeyword,
                        sort,
                        offset,
                        safeSize),
                        currencyCode);
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);

        return new PageResponse<>(items, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public PublicCourseDetailResponse getCourseDetail(String id) {
        ManagedContentType contentType = parseContentType(id);
        Long contentId = parseContentId(id);
        String currencyCode = commerceSettingsService.getDefaultCurrency();

        return switch (contentType) {
            case LESSON -> {
                Lesson lesson = lessonRepository
                        .findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
                                contentId,
                                ContentStatus.PUBLISHED,
                                TeacherStatus.APPROVED)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
                yield contentManagementMapper.toPublicCourseDetail(lesson, currencyCode);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = coursePackageRepository
                        .findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
                                contentId,
                                ContentStatus.PUBLISHED,
                                TeacherStatus.APPROVED)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published package was not found."));
                yield contentManagementMapper.toPublicCourseDetail(
                        coursePackage,
                        packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(
                                coursePackage.getId(),
                                PageRequest.of(0, PUBLIC_PACKAGE_ITEM_LIMIT)).getContent(),
                        currencyCode);
            }
            case MODULE -> throw new ResponseStatusException(NOT_FOUND, "Published course was not found.");
        };
    }

    private long countPublicCourses(
            Long levelId,
            Long categoryId,
            AccessType accessTypeFilter,
            LearningType learningTypeFilter,
            String normalizedKeyword) {
        Query query = entityManager.createNativeQuery(
                "select count(*) from (" + publicCourseUnionSql(
                        levelId,
                        categoryId,
                        accessTypeFilter,
                        learningTypeFilter,
                        normalizedKeyword) + ") public_courses");
        bindPublicCourseParams(query, levelId, categoryId, accessTypeFilter, learningTypeFilter, normalizedKeyword);
        return ((Number) query.getSingleResult()).longValue();
    }

    private List<CourseKey> findPublicCourseKeys(
            Long levelId,
            Long categoryId,
            AccessType accessTypeFilter,
            LearningType learningTypeFilter,
            String normalizedKeyword,
            String sort,
            long offset,
            int size) {
        Query query = entityManager.createNativeQuery(
                "select item_type, item_id from (" + publicCourseUnionSql(
                        levelId,
                        categoryId,
                        accessTypeFilter,
                        learningTypeFilter,
                        normalizedKeyword) + ") public_courses" + publicCourseOrderClause(sort));
        bindPublicCourseParams(query, levelId, categoryId, accessTypeFilter, learningTypeFilter, normalizedKeyword);
        query.setFirstResult(offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset);
        query.setMaxResults(size);

        List<?> rows = query.getResultList();
        List<CourseKey> keys = new ArrayList<>();
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Object[] row) || row.length < 2) {
                throw new IllegalStateException("Public course query returned an unexpected row shape.");
            }
            keys.add(new CourseKey(
                    ManagedContentType.fromValue(row[0].toString()),
                    ((Number) row[1]).longValue()));
        }
        return keys;
    }

    private List<PublicCourseSummaryResponse> loadCourseSummaries(List<CourseKey> keys, String currencyCode) {
        if (keys.isEmpty()) {
            return List.of();
        }

        List<Long> lessonIds = new ArrayList<>();
        List<Long> packageIds = new ArrayList<>();
        for (CourseKey key : keys) {
            if (key.type() == ManagedContentType.LESSON) {
                lessonIds.add(key.id());
            } else if (key.type() == ManagedContentType.PACKAGE) {
                packageIds.add(key.id());
            }
        }

        Map<Long, Lesson> lessonsById = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            for (Lesson lesson : lessonRepository.findByIdIn(lessonIds)) {
                lessonsById.put(lesson.getId(), lesson);
            }
        }

        Map<Long, CoursePackage> packagesById = new HashMap<>();
        if (!packageIds.isEmpty()) {
            for (CoursePackage coursePackage : coursePackageRepository.findByIdIn(packageIds)) {
                packagesById.put(coursePackage.getId(), coursePackage);
            }
        }

        List<PublicCourseSummaryResponse> items = new ArrayList<>();
        for (CourseKey key : keys) {
            if (key.type() == ManagedContentType.LESSON) {
                Lesson lesson = lessonsById.get(key.id());
                if (lesson != null) {
                    items.add(contentManagementMapper.toPublicCourseSummary(lesson, currencyCode));
                }
            } else if (key.type() == ManagedContentType.PACKAGE) {
                CoursePackage coursePackage = packagesById.get(key.id());
                if (coursePackage != null) {
                    items.add(contentManagementMapper.toPublicCourseSummary(coursePackage, currencyCode));
                }
            }
        }
        return items;
    }

    private String publicCourseUnionSql(
            Long levelId,
            Long categoryId,
            AccessType accessTypeFilter,
            LearningType learningTypeFilter,
            String normalizedKeyword) {
        StringBuilder lessonSql = new StringBuilder("""
                select 'LESSON' as item_type, l.id as item_id, l.title as title
                from lessons l
                join teacher_profiles tp on tp.id = l.teacher_id
                where l.content_status = 'PUBLISHED'
                  and tp.teacher_status = 'APPROVED'
                  and tp.public_visible = true
                """);
        appendPublicCourseFilters(lessonSql, "l", levelId, categoryId, accessTypeFilter, learningTypeFilter);
        if (normalizedKeyword != null) {
            lessonSql.append(" and (lower(l.title) like :keyword or lower(coalesce(l.summary, '')) like :keyword)");
        }

        StringBuilder packageSql = new StringBuilder("""
                select 'PACKAGE' as item_type, p.id as item_id, p.title as title
                from course_packages p
                join teacher_profiles tp on tp.id = p.teacher_id
                where p.content_status = 'PUBLISHED'
                  and tp.teacher_status = 'APPROVED'
                  and tp.public_visible = true
                """);
        appendPublicCourseFilters(packageSql, "p", levelId, categoryId, accessTypeFilter, learningTypeFilter);
        if (normalizedKeyword != null) {
            packageSql.append(" and (lower(p.title) like :keyword or lower(coalesce(p.description, '')) like :keyword)");
        }

        return lessonSql + " union all " + packageSql;
    }

    private void appendPublicCourseFilters(
            StringBuilder sql,
            String alias,
            Long levelId,
            Long categoryId,
            AccessType accessTypeFilter,
            LearningType learningTypeFilter) {
        if (levelId != null) {
            sql.append(" and ").append(alias).append(".level_id = :levelId");
        }
        if (categoryId != null) {
            sql.append(" and ").append(alias).append(".category_id = :categoryId");
        }
        if (accessTypeFilter != null) {
            sql.append(" and ").append(alias).append(".access_type = :accessType");
        }
        if (learningTypeFilter != null) {
            sql.append(" and ").append(alias).append(".learning_type = :learningType");
        }
    }

    private void bindPublicCourseParams(
            Query query,
            Long levelId,
            Long categoryId,
            AccessType accessTypeFilter,
            LearningType learningTypeFilter,
            String normalizedKeyword) {
        if (levelId != null) {
            query.setParameter("levelId", levelId);
        }
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId);
        }
        if (accessTypeFilter != null) {
            query.setParameter("accessType", accessTypeFilter.name());
        }
        if (learningTypeFilter != null) {
            query.setParameter("learningType", learningTypeFilter.name());
        }
        if (normalizedKeyword != null) {
            query.setParameter("keyword", "%" + normalizedKeyword + "%");
        }
    }

    private String publicCourseOrderClause(String sort) {
        String normalizedSort = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedSort) {
            case "title" -> " order by lower(title) asc, item_type asc, item_id asc";
            case "newest" -> " order by item_id desc, item_type asc";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Supported course sort options are newest and title.");
        };
    }

    private AccessType parseAccessType(String accessType) {
        if (accessType == null || accessType.isBlank()) {
            return null;
        }

        try {
            return AccessType.valueOf(accessType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid access type filter.");
        }
    }

    private LearningType parseLearningType(String learningType) {
        if (learningType == null || learningType.isBlank()) {
            return null;
        }

        try {
            return LearningType.valueOf(learningType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid learning type filter.");
        }
    }

    private ManagedContentType parseContentType(String id) {
        String[] parts = splitId(id);
        return ManagedContentType.fromValue(parts[0]);
    }

    private Long parseContentId(String id) {
        String[] parts = splitId(id);
        try {
            return Long.valueOf(parts[1]);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid public course id format.");
        }
    }

    private String[] splitId(String id) {
        if (id == null || !id.contains("-")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid public course id format.");
        }

        String[] parts = id.split("-", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid public course id format.");
        }
        return parts;
    }

    private record CourseKey(ManagedContentType type, Long id) {
    }
}
