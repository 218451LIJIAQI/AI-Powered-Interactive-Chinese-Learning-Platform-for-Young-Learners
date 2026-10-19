package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.PublicCourseDetailResponse;
import com.example.aichinese.dto.content.PublicCourseSummaryResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
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
    private final LearningModuleRepository learningModuleRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final ContentManagementMapper contentManagementMapper;
    private final EntityManager entityManager;

    public PublicContentService(
            LessonRepository lessonRepository,
            LearningModuleRepository learningModuleRepository,
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            ContentManagementMapper contentManagementMapper,
            EntityManager entityManager) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicCourseSummaryResponse> discoverCourses(
            String keyword,
            Long levelId,
            Long categoryId,
            String learningType,
            String sort,
            int page,
            int size) {
        LearningType learningTypeFilter = parseLearningType(learningType);
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);
        PublicCourseFilters filters = new PublicCourseFilters(
                levelId,
                categoryId,
                learningTypeFilter,
                normalizedKeyword);

        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        long totalItems = countPublicCourses(filters);
        long offset = (long) safePage * safeSize;
        List<PublicCourseSummaryResponse> items = offset >= totalItems
                ? List.of()
                : loadCourseSummaries(findPublicCourseKeys(
                        filters,
                        sort,
                        offset,
                        safeSize));
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);

        return new PageResponse<>(items, safePage, safeSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public PublicCourseDetailResponse getCourseDetail(String id) {
        CourseKey courseKey = parseCourseKey(id);

        return switch (courseKey.type()) {
            case LESSON -> {
                Lesson lesson = lessonRepository
                        .findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
                                courseKey.id(),
                                ContentStatus.PUBLISHED,
                                TeacherStatus.APPROVED)
                        .filter(ContentVisibilityPolicy::isLessonVisible)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
                yield contentManagementMapper.toPublicCourseDetail(lesson);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = coursePackageRepository
                        .findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
                                courseKey.id(),
                                ContentStatus.PUBLISHED,
                                TeacherStatus.APPROVED)
                        .filter(ContentVisibilityPolicy::isPackageVisible)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published course was not found."));
                yield contentManagementMapper.toPublicCourseDetail(coursePackage, publicPackageItems(coursePackage));
            }
            case MODULE -> throw new ResponseStatusException(NOT_FOUND, "Published course was not found.");
        };
    }

    /**
     * Items of a published course that are themselves visible (FR-CONT-13, FR-PUB-01): a course may list
     * lessons or modules that are still Draft, Submitted or Rejected, and their titles must stay private.
     */
    private List<PackageItem> publicPackageItems(CoursePackage coursePackage) {
        List<PackageItem> items = packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(
                coursePackage.getId(),
                PageRequest.of(0, PUBLIC_PACKAGE_ITEM_LIMIT)).getContent();
        Set<Long> visibleLessonIds = lessonRepository.findAllById(itemIds(items, PackageItemType.LESSON)).stream()
                .filter(ContentVisibilityPolicy::isLessonVisible)
                .map(Lesson::getId)
                .collect(Collectors.toSet());
        Set<Long> visibleModuleIds = learningModuleRepository.findAllById(itemIds(items, PackageItemType.MODULE)).stream()
                .filter(module -> module.getContentStatus() == ContentStatus.PUBLISHED
                        && ContentVisibilityPolicy.isActiveApprovedTeacher(module.getTeacher()))
                .map(LearningModule::getId)
                .collect(Collectors.toSet());
        return items.stream()
                .filter(item -> item.getItemType() == PackageItemType.LESSON
                        ? visibleLessonIds.contains(item.getItemId())
                        : visibleModuleIds.contains(item.getItemId()))
                .toList();
    }

    private static List<Long> itemIds(List<PackageItem> items, PackageItemType type) {
        return items.stream().filter(item -> item.getItemType() == type).map(PackageItem::getItemId).toList();
    }

    private long countPublicCourses(
            PublicCourseFilters filters) {
        Query query = entityManager.createNativeQuery(
                "select count(*) from (" + publicCourseUnionSql(filters) + ") public_courses");
        bindPublicCourseParams(query, filters);
        return ((Number) query.getSingleResult()).longValue();
    }

    private List<CourseKey> findPublicCourseKeys(
            PublicCourseFilters filters,
            String sort,
            long offset,
            int size) {
        Query query = entityManager.createNativeQuery(
                "select item_type, item_id from (" + publicCourseUnionSql(filters)
                        + ") public_courses" + publicCourseOrderClause(sort));
        bindPublicCourseParams(query, filters);
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

    private List<PublicCourseSummaryResponse> loadCourseSummaries(List<CourseKey> keys) {
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
                    items.add(contentManagementMapper.toPublicCourseSummary(lesson));
                }
            } else if (key.type() == ManagedContentType.PACKAGE) {
                CoursePackage coursePackage = packagesById.get(key.id());
                if (coursePackage != null) {
                    items.add(contentManagementMapper.toPublicCourseSummary(coursePackage));
                }
            }
        }
        return items;
    }

    private static String publicCourseUnionSql(PublicCourseFilters filters) {
        StringBuilder lessonSql = new StringBuilder("""
                select 'LESSON' as item_type, l.id as item_id, l.title as title, l.created_at as created_at
                from lessons l
                join teacher_profiles tp on tp.id = l.teacher_id
                join users tu on tu.id = tp.user_id
                left join learning_modules lm on lm.id = l.module_id
                where l.content_status = 'PUBLISHED'
                  and (lm.id is null or lm.content_status = 'PUBLISHED')
                  and tp.teacher_status = 'APPROVED'
                  and tp.public_visible = true
                  and tu.active = true
                """);
        appendPublicCourseFilters(lessonSql, "l", filters);
        if (filters.keyword() != null) {
            lessonSql.append(" and (lower(l.title) like :keyword or lower(coalesce(l.summary, '')) like :keyword)");
        }

        StringBuilder packageSql = new StringBuilder("""
                select 'PACKAGE' as item_type, p.id as item_id, p.title as title, p.created_at as created_at
                from course_packages p
                join teacher_profiles tp on tp.id = p.teacher_id
                join users tu on tu.id = tp.user_id
                where p.content_status = 'PUBLISHED'
                  and tp.teacher_status = 'APPROVED'
                  and tp.public_visible = true
                  and tu.active = true
                """);
        appendPublicCourseFilters(packageSql, "p", filters);
        if (filters.keyword() != null) {
            packageSql.append(" and (lower(p.title) like :keyword or lower(coalesce(p.description, '')) like :keyword)");
        }

        return lessonSql + " union all " + packageSql;
    }

    private static void appendPublicCourseFilters(
            StringBuilder sql,
            String alias,
            PublicCourseFilters filters) {
        if (filters.levelId() != null) {
            sql.append(" and ").append(alias).append(".level_id = :levelId");
        }
        if (filters.categoryId() != null) {
            sql.append(" and ").append(alias).append(".category_id = :categoryId");
        }
        if (filters.learningType() != null) {
            sql.append(" and ").append(alias).append(".learning_type = :learningType");
        }
    }

    private static void bindPublicCourseParams(Query query, PublicCourseFilters filters) {
        if (filters.levelId() != null) {
            query.setParameter("levelId", filters.levelId());
        }
        if (filters.categoryId() != null) {
            query.setParameter("categoryId", filters.categoryId());
        }
        if (filters.learningType() != null) {
            query.setParameter("learningType", filters.learningType().name());
        }
        if (filters.keyword() != null) {
            query.setParameter("keyword", "%" + filters.keyword() + "%");
        }
    }

    private static String publicCourseOrderClause(String sort) {
        String normalizedSort = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedSort) {
            case "title" -> " order by lower(title) asc, item_type asc, item_id asc";
            // Lesson and package ids come from separate sequences, so recency must use created_at.
            case "newest" -> " order by created_at desc, item_id desc, item_type asc";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Supported course sort options are newest and title.");
        };
    }

    private static LearningType parseLearningType(String learningType) {
        if (learningType == null || learningType.isBlank()) {
            return null;
        }

        return LearningType.tryFrom(learningType)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid learning type filter."));
    }

    private static CourseKey parseCourseKey(String id) {
        String[] parts = splitId(id);
        try {
            return new CourseKey(ManagedContentType.fromValue(parts[0]), Long.valueOf(parts[1]));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid public course id format.");
        }
    }

    private static String[] splitId(String id) {
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

    private record PublicCourseFilters(
            Long levelId,
            Long categoryId,
            LearningType learningType,
            String keyword) {
    }
}
