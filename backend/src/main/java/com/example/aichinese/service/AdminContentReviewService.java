package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

@Service
public class AdminContentReviewService {

    private static final int ADMIN_PACKAGE_ITEM_LIMIT = 100;

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
        this.learningModuleRepository = learningModuleRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.packageItemRepository = packageItemRepository;
        this.contentModerationRecordRepository = contentModerationRecordRepository;
        this.contentManagementMapper = contentManagementMapper;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContentSubmissionResponse> listSubmissions(
            String contentType,
            String status,
            Long teacherId,
            String keyword,
            int page,
            int size) {
        // PREVIOUSLY: this method called {@code learningModuleRepository.findAll()},
        // {@code lessonRepository.findAll()} and {@code coursePackageRepository.findAll()}
        // unconditionally, then filtered by status / teacher / keyword in JVM.
        // The IO cost scaled linearly with the global content catalogue on
        // every admin search request, regardless of how narrow the filter was.
        //
        // The new implementation builds three JPA {@link org.springframework.data.jpa.domain.Specification}
        // predicates (one per entity, since the keyword field name differs:
        // module=description / lesson=summary / package=description) and lets
        // the database execute the {@code WHERE status = ? AND teacher_id = ?
        // AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ?)} clause
        // against the appropriate column indexes. The eager-fetch joins
        // (teacher/level/category) are preserved via the repositories' own
        // {@code @EntityGraph} overrides of {@code findAll(Specification)}.
        ManagedContentType filterType = contentType == null || contentType.isBlank()
                ? null
                : ManagedContentType.fromValue(contentType);
        ContentStatus filterStatus = parseStatus(status);
        // Shared normalisation via TextUtils mirrors the same pattern in
        // PublicContentService / AdminTeacherManagementService.
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);

        List<ContentSubmissionResponse> submissions = new ArrayList<>();

        if (filterType == null || filterType == ManagedContentType.MODULE) {
            submissions.addAll(learningModuleRepository
                    .findAll(buildModuleSpecification(filterStatus, teacherId, normalizedKeyword))
                    .stream()
                    .map(contentManagementMapper::toSubmissionResponse)
                    .toList());
        }

        if (filterType == null || filterType == ManagedContentType.LESSON) {
            submissions.addAll(lessonRepository
                    .findAll(buildLessonSpecification(filterStatus, teacherId, normalizedKeyword))
                    .stream()
                    .map(contentManagementMapper::toSubmissionResponse)
                    .toList());
        }

        if (filterType == null || filterType == ManagedContentType.PACKAGE) {
            submissions.addAll(coursePackageRepository
                    .findAll(buildPackageSpecification(filterStatus, teacherId, normalizedKeyword))
                    .stream()
                    .map(contentManagementMapper::toSubmissionResponse)
                    .toList());
        }

        // The in-memory sort + paginate is unavoidable here because the
        // result spans three tables; however it now operates on
        // already-filtered rows (typically tens at most) rather than the
        // entire content catalogue.
        List<ContentSubmissionResponse> sorted = submissions.stream()
                .sorted(Comparator.comparing(ContentSubmissionResponse::updatedAt).reversed())
                .toList();

        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        int startIndex = PaginationUtils.startIndex(safePage, safeSize, sorted.size());
        int endIndex = PaginationUtils.endIndex(startIndex, safeSize, sorted.size());
        List<ContentSubmissionResponse> items = sorted.subList(startIndex, endIndex);
        int totalPages = PaginationUtils.totalPages(sorted.size(), safeSize);

        return new PageResponse<>(items, safePage, safeSize, sorted.size(), totalPages);
    }

    /**
     * Builds a {@link org.springframework.data.jpa.domain.Specification} for
     * the admin module search. Predicate composition uses
     * {@link jakarta.persistence.criteria.Predicate} so JPA emits one
     * compound {@code WHERE} clause with all three filters in a single
     * statement.
     */
    private static org.springframework.data.jpa.domain.Specification<LearningModule> buildModuleSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
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
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static org.springframework.data.jpa.domain.Specification<Lesson> buildLessonSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("contentStatus"), status));
            }
            if (teacherId != null) {
                predicates.add(cb.equal(root.get("teacher").get("id"), teacherId));
            }
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                // Lesson uses {@code summary} (not {@code description}) for
                // the searchable secondary text field — matches the legacy
                // JVM-side filter exactly so the search contract is preserved.
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), like)));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static org.springframework.data.jpa.domain.Specification<CoursePackage> buildPackageSpecification(
            ContentStatus status, Long teacherId, String normalizedKeyword) {
        return (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
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
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
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
                // Refuse double-archive — without this guard a second click on
                // the Archive button would write a fresh ContentModerationRecord
                // and a fresh AuditLog row even though the entity is already
                // ARCHIVED, polluting the moderation history. Archive is
                // intentionally permissive about the *prior* status (any
                // state → ARCHIVED is allowed) but the no-op case must be
                // refused, mirroring the refund/payout state-machine guards
                // added in earlier waves.
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

        recordModeration(adminUser, contentType, id, "ARCHIVE", note);
        auditLogService.record(adminUser, "ADMIN_CONTENT_ARCHIVE", contentType.name(), id,
                "Admin archived content.");
        return response;
    }

    private void requireNotAlreadyArchived(ContentStatus currentStatus, String entityName) {
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
            return ContentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    private void recordModeration(User adminUser, ManagedContentType contentType, Long id, String action, String note) {
        ContentModerationRecord record = new ContentModerationRecord();
        record.setAdminUser(adminUser);
        record.setTargetType(contentType.name());
        record.setTargetId(id);
        record.setActionTaken(action);
        record.setNote(TextUtils.trimToNull(note));
        contentModerationRecordRepository.save(record);
    }
}
