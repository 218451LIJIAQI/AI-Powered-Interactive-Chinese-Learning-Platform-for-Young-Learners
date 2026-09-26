package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.ContentSubmissionDetailResponse;
import com.example.aichinese.dto.content.ContentSubmissionResponse;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.ContentModerationRecordRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

@Service
public class AdminContentReviewService {

    private static final Logger log = LoggerFactory.getLogger(AdminContentReviewService.class);

    private static final int ADMIN_PACKAGE_ITEM_LIMIT = 100;
    private static final int MERGE_BATCH_SIZE = 100;
    private static final int MAX_MODERATION_NOTE_LENGTH = 1000;

    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final ContentModerationRecordRepository contentModerationRecordRepository;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public AdminContentReviewService(
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            ContentModerationRecordRepository contentModerationRecordRepository,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService,
            UserRepository userRepository) {
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.contentModerationRecordRepository = Objects.requireNonNull(contentModerationRecordRepository, "contentModerationRecordRepository");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional(readOnly = true)
    public PageResponse<ContentSubmissionResponse> listSubmissions(
            String contentType,
            String status,
            Long teacherId,
            String keyword,
            int page,
            int size) {
        ManagedContentType filterType = contentType == null || contentType.isBlank()
                ? null
                : ManagedContentType.fromValue(contentType);
        ContentStatus filterStatus = parseStatus(status);
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);

        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        List<SubmissionCursor<?>> cursors = new ArrayList<>();
        long totalItems = 0L;

        if (filterType == null || filterType == ManagedContentType.MODULE) {
            Specification<LearningModule> specification = buildModuleSpecification(filterStatus, teacherId, normalizedKeyword);
            SubmissionCursor<LearningModule> cursor = new SubmissionCursor<>(
                    pageable -> learningModuleRepository.findAll(specification, pageable),
                    contentManagementMapper::toSubmissionResponse,
                    sort);
            totalItems += cursor.totalItems();
            cursors.add(cursor);
        }

        if (filterType == null || filterType == ManagedContentType.LESSON) {
            Specification<Lesson> specification = buildLessonSpecification(filterStatus, teacherId, normalizedKeyword);
            SubmissionCursor<Lesson> cursor = new SubmissionCursor<>(
                    pageable -> lessonRepository.findAll(specification, pageable),
                    contentManagementMapper::toSubmissionResponse,
                    sort);
            totalItems += cursor.totalItems();
            cursors.add(cursor);
        }

        if (filterType == null || filterType == ManagedContentType.PACKAGE) {
            Specification<CoursePackage> specification = buildPackageSpecification(filterStatus, teacherId, normalizedKeyword);
            SubmissionCursor<CoursePackage> cursor = new SubmissionCursor<>(
                    pageable -> coursePackageRepository.findAll(specification, pageable),
                    contentManagementMapper::toSubmissionResponse,
                    sort);
            totalItems += cursor.totalItems();
            cursors.add(cursor);
        }

        List<ContentSubmissionResponse> items = new ArrayList<>(safeSize);
        long firstIndex = (long) safePage * safeSize;
        if (firstIndex < totalItems) {
            Comparator<ContentSubmissionResponse> newestFirst = Comparator
                    .comparing(ContentSubmissionResponse::updatedAt).reversed()
                    .thenComparing(ContentSubmissionResponse::id, Comparator.reverseOrder());
            long seen = 0;
            while (seen < firstIndex + safeSize) {
                SubmissionCursor<?> next = null;
                ContentSubmissionResponse nextItem = null;
                for (SubmissionCursor<?> cursor : cursors) {
                    ContentSubmissionResponse candidate = cursor.peek();
                    if (candidate != null && (nextItem == null || newestFirst.compare(candidate, nextItem) < 0)) {
                        next = cursor;
                        nextItem = candidate;
                    }
                }
                if (next == null) {
                    break;
                }
                next.advance();
                if (seen++ >= firstIndex) {
                    items.add(nextItem);
                }
            }
        }
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);

        return new PageResponse<>(items, safePage, safeSize, totalItems, totalPages);
    }

    private static final class SubmissionCursor<T> {
        private final Function<Pageable, Page<T>> fetch;
        private final Function<T, ContentSubmissionResponse> map;
        private final Sort sort;
        private List<T> batch;
        private int page;
        private int index;
        private long totalItems;
        private ContentSubmissionResponse pending;

        private SubmissionCursor(Function<Pageable, Page<T>> fetch, Function<T, ContentSubmissionResponse> map, Sort sort) {
            this.fetch = fetch;
            this.map = map;
            this.sort = sort;
            loadPage();
        }

        private long totalItems() {
            return totalItems;
        }

        private ContentSubmissionResponse peek() {
            if (index >= batch.size()) {
                if ((long) page * MERGE_BATCH_SIZE >= totalItems) {
                    return null;
                }
                loadPage();
            }
            if (index >= batch.size()) {
                return null;
            }
            if (pending == null) {
                pending = map.apply(batch.get(index));
            }
            return pending;
        }

        private void advance() {
            index++;
            pending = null;
        }

        private void loadPage() {
            Page<T> result = fetch.apply(PageRequest.of(page++, MERGE_BATCH_SIZE, sort));
            totalItems = result.getTotalElements();
            batch = result.getContent();
            index = 0;
            pending = null;
        }
    }

    private static Specification<LearningModule> buildModuleSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return buildContentSpecification(status, teacherId, normalizedKeyword, "description");
    }

    private static Specification<Lesson> buildLessonSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return buildContentSpecification(status, teacherId, normalizedKeyword, "summary");
    }

    private static Specification<CoursePackage> buildPackageSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return buildContentSpecification(status, teacherId, normalizedKeyword, "description");
    }

    private static <T> Specification<T> buildContentSpecification(
            ContentStatus status,
            Long teacherId,
            String normalizedKeyword,
            String secondaryTextField) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("contentStatus"), status));
            }
            if (teacherId != null) {
                predicates.add(cb.equal(root.get("teacher").get("id"), teacherId));
            }
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get(secondaryTextField), "")), like)));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public ContentSubmissionDetailResponse getSubmissionDetail(ManagedContentType contentType, Long id) {
        return switch (contentType) {
            case MODULE -> {
                LearningModule module = findModule(id);
                yield contentManagementMapper.toSubmissionDetailResponse(module, List.of());
            }
            case LESSON -> {
                Lesson lesson = findLesson(id);
                yield contentManagementMapper.toSubmissionDetailResponse(lesson);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = findPackage(id);
                yield contentManagementMapper.toSubmissionDetailResponse(
                        coursePackage,
                        packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(
                                coursePackage.getId(),
                                PageRequest.of(0, ADMIN_PACKAGE_ITEM_LIMIT)).getContent());
            }
        };
    }

    @Transactional
    public ContentSubmissionDetailResponse archiveContent(
            SecurityUserDetails currentUser,
            ManagedContentType contentType,
            Long id,
            String note) {
        User adminUser = findAdmin(currentUser);

        ContentSubmissionDetailResponse response = switch (contentType) {
            case MODULE -> {
                LearningModule module = findModule(id);
                requireNotAlreadyArchived(module.getContentStatus(), "module");
                module.setContentStatus(ContentStatus.ARCHIVED);
                learningModuleRepository.save(module);
                yield contentManagementMapper.toSubmissionDetailResponse(module, List.of());
            }
            case LESSON -> {
                Lesson lesson = findLesson(id);
                requireNotAlreadyArchived(lesson.getContentStatus(), "lesson");
                lesson.setContentStatus(ContentStatus.ARCHIVED);
                lessonRepository.save(lesson);
                yield contentManagementMapper.toSubmissionDetailResponse(lesson);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = findPackage(id);
                requireNotAlreadyArchived(coursePackage.getContentStatus(), "package");
                coursePackage.setContentStatus(ContentStatus.ARCHIVED);
                coursePackageRepository.save(coursePackage);
                yield contentManagementMapper.toSubmissionDetailResponse(
                        coursePackage,
                        packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(
                                coursePackage.getId(),
                                PageRequest.of(0, ADMIN_PACKAGE_ITEM_LIMIT)).getContent());
            }
        };

        recordModeration(adminUser, contentType, id, note);
        log.info("Content archived: type={}, id={}, adminUserId={}", contentType, id, adminUser.getId());
        auditLogService.record(adminUser, "ADMIN_CONTENT_ARCHIVE", contentType.name(), id,
                "Admin archived content.");
        return response;
    }

    private static void requireNotAlreadyArchived(ContentStatus currentStatus, String entityName) {
        if (currentStatus == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "This " + entityName + " is already archived.");
        }
    }

    private ContentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return ContentStatus.fromValue(status);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid content status filter.");
        }
    }

    private LearningModule findModule(Long id) {
        return learningModuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested module was not found."));
    }

    private Lesson findLesson(Long id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson was not found."));
    }

    private CoursePackage findPackage(Long id) {
        return coursePackageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested package was not found."));
    }

    private User findAdmin(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
    }

    private void recordModeration(User adminUser, ManagedContentType contentType, Long id, String note) {
        ContentModerationRecord record = new ContentModerationRecord();
        record.setAdminUser(adminUser);
        record.setTargetType(contentType.name());
        record.setTargetId(id);
        record.setActionTaken("ARCHIVE");
        record.setNote(AiInputSanitizer.sanitize(note, MAX_MODERATION_NOTE_LENGTH));
        contentModerationRecordRepository.save(record);
    }
}
