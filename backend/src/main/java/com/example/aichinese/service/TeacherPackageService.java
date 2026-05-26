package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.CreateCoursePackageRequest;
import com.example.aichinese.dto.content.PackageItemRequest;
import com.example.aichinese.dto.content.UpdateCoursePackageRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Teacher-side CRUD for {@link CoursePackage}s — the bundling unit
 * that students purchase to unlock multiple modules / lessons in one
 * transaction.
 *
 * <p>A package is composed of {@link PackageItem}s referencing either
 * {@link LearningModule}s or individual {@link Lesson}s. Both kinds
 * of items must be owned by the same teacher; the ownership and
 * status checks delegate to {@link TeacherModuleService} /
 * {@link TeacherLessonService} so the rules live in one place. Pricing
 * inputs flow through {@link TeacherContentAccessService}'s
 * {@code BigDecimal} validation so currency arithmetic never crosses
 * boundaries.</p>
 */
@Service
public class TeacherPackageService {

    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final TeacherModuleService teacherModuleService;
    private final TeacherLessonService teacherLessonService;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;

    public TeacherPackageService(
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            TeacherModuleService teacherModuleService,
            TeacherLessonService teacherLessonService,
            TeacherContentAccessService teacherContentAccessService,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService) {
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.teacherModuleService = Objects.requireNonNull(teacherModuleService, "teacherModuleService");
        this.teacherLessonService = Objects.requireNonNull(teacherLessonService, "teacherLessonService");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional(readOnly = true)
    public PageResponse<CoursePackageResponse> getTeacherPackages(SecurityUserDetails currentUser, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<CoursePackage> packagePage = coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        List<CoursePackageResponse> items = packagePage.getContent().stream()
                .map(this::toCoursePackageResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, packagePage.getTotalElements(), packagePage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CoursePackageResponse getTeacherPackage(SecurityUserDetails currentUser, Long packageId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return toCoursePackageResponse(findOwnedPackage(currentUser, packageId));
    }

    @Transactional
    public CoursePackageResponse createPackage(SecurityUserDetails currentUser, CreateCoursePackageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Package request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        CoursePackage coursePackage = new CoursePackage();
        teacherContentAccessService.attachPackageMetadata(
                coursePackage,
                teacherProfile,
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.accessType(),
                request.price(),
                request.learningType(),
                request.coverImageUrl());
        // No admin review flow: teacher-created packages are immediately
        // available in the public course catalogue.
        coursePackage.setContentStatus(ContentStatus.PUBLISHED);
        coursePackage.setRejectionReason(null);

        CoursePackage saved = coursePackageRepository.save(coursePackage);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_CREATE",
                "CoursePackage",
                saved.getId(),
                "Teacher created and published a course package.");
        return toCoursePackageResponse(saved);
    }

    @Transactional
    public CoursePackageResponse updatePackage(
            SecurityUserDetails currentUser,
            Long packageId,
            UpdateCoursePackageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Package request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateEditableStatus(coursePackage.getContentStatus(), "Package");
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        teacherContentAccessService.attachPackageMetadata(
                coursePackage,
                teacherProfile,
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.accessType(),
                request.price(),
                request.learningType(),
                request.coverImageUrl());

        CoursePackage saved = coursePackageRepository.save(coursePackage);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_UPDATE",
                "CoursePackage",
                saved.getId(),
                "Teacher updated a course package draft.");
        return toCoursePackageResponse(saved);
    }

    @Transactional
    public CoursePackageResponse archivePackage(SecurityUserDetails currentUser, Long packageId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateArchivableStatus(coursePackage.getContentStatus(), "Package");

        coursePackage.setContentStatus(ContentStatus.ARCHIVED);
        coursePackageRepository.save(coursePackage);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_ARCHIVE",
                "CoursePackage",
                coursePackage.getId(),
                "Teacher archived a course package.");
        return toCoursePackageResponse(coursePackage);
    }

    @Transactional
    public CoursePackageResponse addPackageItem(SecurityUserDetails currentUser, Long packageId, PackageItemRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Package item request is required.");
        }
        if (request.itemType() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Item type is required.");
        }
        if (request.itemId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Item id is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateEditableStatus(coursePackage.getContentStatus(), "Package");
        if (packageItemRepository.existsByCoursePackageIdAndItemTypeAndItemId(
                coursePackage.getId(),
                request.itemType(),
                request.itemId())) {
            throw new ResponseStatusException(BAD_REQUEST, "This item is already included in the package.");
        }

        PackageItem packageItem = new PackageItem();
        packageItem.setCoursePackage(coursePackage);
        packageItem.setItemType(request.itemType());
        packageItem.setItemId(request.itemId());
        packageItem.setSortOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        packageItem.setTitleSnapshot(resolvePackageItemTitle(currentUser, request.itemType(), request.itemId()));
        packageItemRepository.save(packageItem);

        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_ITEM_ADD",
                "CoursePackage",
                coursePackage.getId(),
                "Teacher added a package item.");
        return toCoursePackageResponse(coursePackage);
    }

    @Transactional
    public CoursePackageResponse removePackageItem(SecurityUserDetails currentUser, Long packageId, Long itemId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateEditableStatus(coursePackage.getContentStatus(), "Package");

        PackageItem item = packageItemRepository.findByIdAndCoursePackageTeacherUserId(itemId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested package item was not found."));

        if (!item.getCoursePackage().getId().equals(coursePackage.getId())) {
            throw new ResponseStatusException(NOT_FOUND, "Requested package item was not found.");
        }

        packageItemRepository.delete(item);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_ITEM_REMOVE",
                "CoursePackage",
                coursePackage.getId(),
                "Teacher removed a package item.");
        return toCoursePackageResponse(coursePackage);
    }

    private CoursePackage findOwnedPackage(SecurityUserDetails currentUser, Long packageId) {
        return coursePackageRepository.findByIdAndTeacherUserId(packageId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested package was not found."));
    }

    private String resolvePackageItemTitle(SecurityUserDetails currentUser, PackageItemType itemType, Long itemId) {
        return switch (itemType) {
            case MODULE -> {
                LearningModule module = teacherModuleService.findOwnedModule(currentUser, itemId);
                if (module.getContentStatus() == ContentStatus.ARCHIVED) {
                    throw new ResponseStatusException(BAD_REQUEST, "Archived modules cannot be added to a package.");
                }
                yield module.getTitle();
            }
            case LESSON -> {
                Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, itemId);
                if (lesson.getContentStatus() == ContentStatus.ARCHIVED) {
                    throw new ResponseStatusException(BAD_REQUEST, "Archived lessons cannot be added to a package.");
                }
                yield lesson.getTitle();
            }
        };
    }

    private CoursePackageResponse toCoursePackageResponse(CoursePackage coursePackage) {
        return contentManagementMapper.toCoursePackageResponse(
                coursePackage,
                packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(coursePackage.getId()));
    }
}
