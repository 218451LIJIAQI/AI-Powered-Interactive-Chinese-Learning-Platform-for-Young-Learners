package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminStudentListItemResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.admin.AdminUserStatusUpdateRequest;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Admin-side user search / detail / activation toggles for both
 * student and teacher accounts.
 *
 * <h2>Soft activation</h2>
 * <p>{@link AdminUserStatusUpdateRequest} flips a user's
 * {@code active} flag rather than deleting the record — preserving
 * referential integrity for past orders, AI usage logs, content
 * ownership, etc. Deactivated users cannot log in (their
 * {@code SessionVersion} is bumped so existing JWTs are revoked
 * instantly) but their data history remains intact for audit.</p>
 *
 * <h2>Listing performance</h2>
 * <p>Filtering / sorting uses JPA {@link Specification} so admins
 * can combine arbitrary criteria (role, status, registration date,
 * search term) without the service ballooning into per-filter
 * methods. Pagination is mandatory; the listing endpoints reject
 * page sizes outside the safe range via {@link PaginationUtils}.</p>
 */
@Service
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherManagementMapper teacherManagementMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public AdminUserManagementService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherManagementMapper teacherManagementMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherManagementMapper = teacherManagementMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> listUsers(
            String role,
            String keyword,
            String status,
            int page,
            int size) {
        int sanitizedPage = PaginationUtils.sanitizePage(page);
        int sanitizedSize = PaginationUtils.sanitizeSize(size);
        RoleName roleFilter = parseRole(role);
        TeacherStatus teacherStatusFilter = parseTeacherStatus(status);
        String keywordFilter = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        Page<Long> userIdPage = userRepository.findAdminUserIds(
                roleFilter != null ? roleFilter.name() : null,
                keywordFilter,
                teacherStatusFilter != null ? teacherStatusFilter.name() : null,
                PageRequest.of(sanitizedPage, sanitizedSize));
        List<Long> pageUserIds = userIdPage.getContent();

        Map<Long, User> usersById = pageUserIds.isEmpty()
                ? Map.of()
                : userRepository.findByIdIn(pageUserIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<Long, TeacherProfile> teacherProfiles = pageUserIds.isEmpty()
                ? Map.of()
                : teacherProfileRepository.findByUserIdIn(pageUserIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (left, right) -> left));

        List<AdminUserListItemResponse> items = pageUserIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(user -> teacherManagementMapper.toAdminUserListItem(user, teacherProfiles.get(user.getId())))
                .toList();

        return new PageResponse<>(items, sanitizedPage, sanitizedSize, userIdPage.getTotalElements(), userIdPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStudentListItemResponse> listStudents(int page, int size) {
        int sanitizedPage = PaginationUtils.sanitizePage(page);
        int sanitizedSize = PaginationUtils.sanitizeSize(size);

        Page<StudentProfile> studentPage = studentProfileRepository.findAll(
                (Specification<StudentProfile>) null,
                PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminStudentListItemResponse> items = studentPage.getContent().stream()
                .map(teacherManagementMapper::toAdminStudentListItem)
                .toList();
        return new PageResponse<>(items, sanitizedPage, sanitizedSize, studentPage.getTotalElements(), studentPage.getTotalPages());
    }

    @Transactional
    public AdminUserListItemResponse updateUserStatus(
            SecurityUserDetails currentUser,
            Long userId,
            AdminUserStatusUpdateRequest request) {
        User adminUser = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Admin user could not be found."));
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "User was not found."));
        TeacherProfile teacherProfile = teacherProfileRepository.findDetailedByUserId(targetUser.getId()).orElse(null);
        boolean nextActive = Boolean.TRUE.equals(request.active());

        if (!nextActive && adminUser.getId().equals(targetUser.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot deactivate your own admin account.");
        }

        if (targetUser.isActive() == nextActive) {
            return teacherManagementMapper.toAdminUserListItem(targetUser, teacherProfile);
        }

        targetUser.setActive(nextActive);
        targetUser.setSessionVersion(targetUser.getSessionVersion() + 1);
        userRepository.save(targetUser);

        if (teacherProfile != null) {
            if (!nextActive) {
                teacherProfile.setPublicVisible(false);
            } else if (teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED) {
                teacherProfile.setPublicVisible(true);
            }
            teacherProfileRepository.save(teacherProfile);
        }

        String actionLabel = nextActive ? "reactivated" : "deactivated";
        notificationService.safeCreateNotification(
                targetUser,
                com.example.aichinese.entity.enums.NotificationType.SYSTEM,
                nextActive ? "Account access restored" : "Account access paused",
                nextActive
                        ? "An administrator restored your account access. You can sign in again."
                        : "An administrator paused your account access. Contact support if you need help.",
                "User",
                targetUser.getId(),
                "/auth",
                adminUser);
        auditLogService.record(
                adminUser,
                nextActive ? "ADMIN_USER_REACTIVATE" : "ADMIN_USER_DEACTIVATE",
                "User",
                targetUser.getId(),
                "Admin " + actionLabel + " user access.");

        return teacherManagementMapper.toAdminUserListItem(targetUser, teacherProfile);
    }

    private RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }

        try {
            return RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid user role filter.");
        }
    }

    private TeacherStatus parseTeacherStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return TeacherStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid teacher status filter.");
        }
    }
}
