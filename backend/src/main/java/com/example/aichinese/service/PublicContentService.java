package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<PublicCourseSummaryResponse> results = new ArrayList<>();

        results.addAll(lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getContentStatus() == ContentStatus.PUBLISHED)
                .filter(lesson -> levelId == null || (lesson.getLevel() != null && lesson.getLevel().getId().equals(levelId)))
                .filter(lesson -> categoryId == null || (lesson.getCategory() != null && lesson.getCategory().getId().equals(categoryId)))
                .filter(lesson -> accessTypeFilter == null || lesson.getAccessType() == accessTypeFilter)
                .filter(lesson -> learningTypeFilter == null || lesson.getLearningType() == learningTypeFilter)
                .filter(lesson -> matchesKeyword(lesson.getTitle(), lesson.getSummary(), normalizedKeyword))
                .map(contentManagementMapper::toPublicCourseSummary)
                .toList());

        results.addAll(coursePackageRepository.findAll().stream()
                .filter(coursePackage -> coursePackage.getContentStatus() == ContentStatus.PUBLISHED)
                .filter(coursePackage -> levelId == null || (coursePackage.getLevel() != null && coursePackage.getLevel().getId().equals(levelId)))
                .filter(coursePackage -> categoryId == null || (coursePackage.getCategory() != null && coursePackage.getCategory().getId().equals(categoryId)))
                .filter(coursePackage -> accessTypeFilter == null || coursePackage.getAccessType() == accessTypeFilter)
                .filter(coursePackage -> learningTypeFilter == null || coursePackage.getLearningType() == learningTypeFilter)
                .filter(coursePackage -> matchesKeyword(coursePackage.getTitle(), coursePackage.getDescription(), normalizedKeyword))
                .map(contentManagementMapper::toPublicCourseSummary)
                .toList());

        List<PublicCourseSummaryResponse> sorted = sortCourses(results, sort);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int startIndex = Math.min(safePage * safeSize, sorted.size());
        int endIndex = Math.min(startIndex + safeSize, sorted.size());
        List<PublicCourseSummaryResponse> items = sorted.subList(startIndex, endIndex);
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

    private boolean matchesKeyword(String title, String description, String keyword) {
        if (keyword == null) {
            return true;
        }

        String haystack = ((title == null ? "" : title) + " " + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
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
