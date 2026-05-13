package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
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
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonActivityRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Predicate;

@Service
public class PublicContentService {

    private final LessonRepository lessonRepository;
    private final LessonActivityRepository lessonActivityRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final ContentManagementMapper contentManagementMapper;

    public PublicContentService(
            LessonRepository lessonRepository,
            LessonActivityRepository lessonActivityRepository,
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            ContentManagementMapper contentManagementMapper) {
        this.lessonRepository = lessonRepository;
        this.lessonActivityRepository = lessonActivityRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.packageItemRepository = packageItemRepository;
        this.contentManagementMapper = contentManagementMapper;
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
        // Shared trim/lowercase/blank-to-null via TextUtils so this matches
        // the identical normalisation in AdminContentReviewService and
        // AdminTeacherManagementService — making future cross-cutting tweaks
        // (e.g. NFKC-normalising user input) a single-place change.
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);

        // Filter at the database layer using JPA Specifications. Previously this
        // method called findAll() on both repositories and filtered every row
        // in-memory, which scaled poorly as the catalog grew. Pushing the
        // predicates down to SQL ensures the query planner can use the existing
        // content_status / category / level indexes.
        Specification<Lesson> lessonSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("contentStatus"), ContentStatus.PUBLISHED));
            if (levelId != null) {
                predicates.add(cb.equal(root.get("level").get("id"), levelId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (accessTypeFilter != null) {
                predicates.add(cb.equal(root.get("accessType"), accessTypeFilter));
            }
            if (learningTypeFilter != null) {
                predicates.add(cb.equal(root.get("learningType"), learningTypeFilter));
            }
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<CoursePackage> packageSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("contentStatus"), ContentStatus.PUBLISHED));
            if (levelId != null) {
                predicates.add(cb.equal(root.get("level").get("id"), levelId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (accessTypeFilter != null) {
                predicates.add(cb.equal(root.get("accessType"), accessTypeFilter));
            }
            if (learningTypeFilter != null) {
                predicates.add(cb.equal(root.get("learningType"), learningTypeFilter));
            }
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<PublicCourseSummaryResponse> results = new ArrayList<>();
        for (Lesson lesson : lessonRepository.findAll(lessonSpec)) {
            results.add(contentManagementMapper.toPublicCourseSummary(lesson));
        }
        for (CoursePackage coursePackage : coursePackageRepository.findAll(packageSpec)) {
            results.add(contentManagementMapper.toPublicCourseSummary(coursePackage));
        }

        // Two entity types still have to be merged + sorted in-memory because
        // they live in different tables, but the working set is now bounded by
        // the active filters rather than the full catalog.
        List<PublicCourseSummaryResponse> sorted = sortCourses(results, sort);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int startIndex = Math.min(safePage * safeSize, sorted.size());
        int endIndex = Math.min(startIndex + safeSize, sorted.size());
        List<PublicCourseSummaryResponse> items = new ArrayList<>(sorted.subList(startIndex, endIndex));
        int totalPages = sorted.isEmpty() ? 0 : (int) Math.ceil((double) sorted.size() / safeSize);

        return new PageResponse<>(items, safePage, safeSize, sorted.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public PublicCourseDetailResponse getCourseDetail(String id) {
        ManagedContentType contentType = parseContentType(id);
        Long contentId = parseContentId(id);

        return switch (contentType) {
            case LESSON -> {
                Lesson lesson = lessonRepository.findByIdAndContentStatus(contentId, ContentStatus.PUBLISHED)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
                yield contentManagementMapper.toPublicCourseDetail(
                        lesson,
                        lessonActivityRepository.findByLessonIdAndContentStatusNotOrderBySortOrderAscCreatedAtAsc(
                                lesson.getId(),
                                ContentStatus.ARCHIVED));
            }
            case PACKAGE -> {
                CoursePackage coursePackage = coursePackageRepository.findByIdAndContentStatus(contentId, ContentStatus.PUBLISHED)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published package was not found."));
                yield contentManagementMapper.toPublicCourseDetail(
                        coursePackage,
                        packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(coursePackage.getId()));
            }
            case MODULE -> throw new ResponseStatusException(NOT_FOUND, "Published course was not found.");
        };
    }

    private List<PublicCourseSummaryResponse> sortCourses(List<PublicCourseSummaryResponse> items, String sort) {
        String normalizedSort = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<PublicCourseSummaryResponse> comparator = switch (normalizedSort) {
            case "title" -> Comparator.comparing(PublicCourseSummaryResponse::title, String.CASE_INSENSITIVE_ORDER);
            case "rating", "popular", "newest" -> Comparator.comparing(PublicCourseSummaryResponse::contentId).reversed();
            default -> Comparator.comparing(PublicCourseSummaryResponse::contentId).reversed();
        };
        return items.stream().sorted(comparator).toList();
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
}
