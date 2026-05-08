package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        this.coursePackageRepository = coursePackageRepository;
        this.packageItemRepository = packageItemRepository;
        this.teacherModuleService = teacherModuleService;
        this.teacherLessonService = teacherLessonService;
        this.teacherContentAccessService = teacherContentAccessService;
        this.contentManagementMapper = contentManagementMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CoursePackageResponse> getTeacherPackages(SecurityUserDetails currentUser) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId()).stream()
                .map(this::toCoursePackageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoursePackageResponse getTeacherPackage(SecurityUserDetails currentUser, Long packageId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return toCoursePackageResponse(findOwnedPackage(currentUser, packageId));
    }

    @Transactional
    public CoursePackageResponse createPackage(SecurityUserDetails currentUser, CreateCoursePackageRequest request) {
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
        coursePackage.setContentStatus(ContentStatus.DRAFT);
        coursePackage.setRejectionReason(null);

        CoursePackage saved = coursePackageRepository.save(coursePackage);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_CREATE",
                "CoursePackage",
                saved.getId(),
                "Teacher created a course package draft.");
        return toCoursePackageResponse(saved);
    }

    @Transactional
    public CoursePackageResponse updatePackage(
            SecurityUserDetails currentUser,
            Long packageId,
            UpdateCoursePackageRequest request) {
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
    public CoursePackageResponse submitPackage(SecurityUserDetails currentUser, Long packageId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateSubmittableStatus(coursePackage.getContentStatus(), "Package");

        coursePackage.setContentStatus(ContentStatus.SUBMITTED);
        coursePackageRepository.save(coursePackage);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_PACKAGE_SUBMIT",
                "CoursePackage",
                coursePackage.getId(),
                "Teacher submitted a course package for admin review.");
        return toCoursePackageResponse(coursePackage);
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
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        CoursePackage coursePackage = findOwnedPackage(currentUser, packageId);
        teacherContentAccessService.validateEditableStatus(coursePackage.getContentStatus(), "Package");

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

    @Transactional(readOnly = true)
    public CoursePackage findOwnedPackage(SecurityUserDetails currentUser, Long packageId) {
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
            case ACTIVITY -> throw new ResponseStatusException(BAD_REQUEST,
                    "Activity package items are not enabled in Phase 6.");
        };
    }

    private CoursePackageResponse toCoursePackageResponse(CoursePackage coursePackage) {
        return contentManagementMapper.toCoursePackageResponse(
                coursePackage,
                packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(coursePackage.getId()));
    }
}
