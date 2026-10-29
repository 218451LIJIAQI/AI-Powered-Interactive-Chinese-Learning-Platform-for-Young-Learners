package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminDeactivationResponse;
import com.example.aichinese.dto.admin.AdminUserDetailResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.account.AccountRoles;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * The admin Users page: search, one account in detail, and deactivating or reactivating an account with a reason.
 * Deactivation signs the account out at once and runs every {@link AccountDeactivationStep}.
 */
@Service
public class AdminUserService {

    public static final String ACTION_DEACTIVATE = "ADMIN_USER_DEACTIVATE";
    public static final String ACTION_REACTIVATE = "ADMIN_USER_REACTIVATE";

    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogQueryService auditLogQueryService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final RefreshTokenService refreshTokenService;
    private final List<AccountDeactivationStep> deactivationSteps;

    public AdminUserService(
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository,
            AuditLogRepository auditLogRepository,
            AuditLogQueryService auditLogQueryService,
            AuditLogService auditLogService,
            NotificationService notificationService,
            RefreshTokenService refreshTokenService,
            List<AccountDeactivationStep> deactivationSteps) {
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogQueryService = auditLogQueryService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.refreshTokenService = refreshTokenService;
        this.deactivationSteps = List.copyOf(deactivationSteps);
    }

    /** {@code keyword} matches the name or e-mail; {@code role} and {@code active} narrow the list. */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> list(String keyword, String role, Boolean active, int page, int size) {
        RoleName roleFilter = parseRole(role);
        String keywordFilter = TextUtils.trimToOptional(keyword)
                .map(value -> "%" + value.toLowerCase(Locale.ROOT) + "%")
                .orElse(null);
        PageRequest pageRequest = PaginationUtils.pageRequest(page, size);
        Page<Long> idPage = userRepository.findAdminUserIds(
                roleFilter == null ? null : roleFilter.name(), keywordFilter, active, pageRequest);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PageResponse.of(List.of(), pageRequest.getPageNumber(), pageRequest.getPageSize(), idPage.getTotalElements());
        }
        Map<Long, User> users = userRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
        Map<Long, TeacherProfile> profiles = teacherProfileRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (left, right) -> left));
        List<AdminUserListItemResponse> items = ids.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .map(user -> toListItem(user, profiles.get(user.getId())))
                .toList();
        return PageResponse.of(items, pageRequest.getPageNumber(), pageRequest.getPageSize(), idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(SecurityUserDetails currentUser, Long userId) {
        return toDetail(currentUser, requireUser(userId));
    }

    /** Signs the account out now, blocks sign-in and applies every deactivation step; the reason is audited. */
    @Transactional
    public AdminDeactivationResponse deactivate(SecurityUserDetails currentUser, Long userId, String reason) {
        User admin = requireUser(currentUser.getUserId());
        User target = requireUser(userId);
        if (admin.getId().equals(target.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot deactivate your own admin account.");
        }
        if (!target.isActive()) {
            return new AdminDeactivationResponse(toDetail(currentUser, target), 0, 0, 0);
        }

        target.setActive(false);
        refreshTokenService.invalidateSessions(target);
        DeactivationTally done = new DeactivationTally();
        deactivationSteps.forEach(step -> step.apply(target, admin, done));

        auditLogService.record(admin, ACTION_DEACTIVATE,
                new AuditTarget(AuditLogQueryService.TARGET_USER, target.getId(), target.getDisplayName()), reason,
                "Admin deactivated the account: signed out, sign-in blocked; " + done.upcomingSessions()
                        + " upcoming live sessions cancelled, " + done.bookings() + " bookings ended, "
                        + done.reviewItems() + " review items back to Draft.");
        return new AdminDeactivationResponse(
                toDetail(currentUser, target), done.upcomingSessions(), done.bookings(), done.reviewItems());
    }

    @Transactional
    public AdminUserDetailResponse reactivate(SecurityUserDetails currentUser, Long userId, String reason) {
        User admin = requireUser(currentUser.getUserId());
        User target = requireUser(userId);
        if (target.getDeletedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "A deleted account can't be reactivated.");
        }
        if (target.isActive()) {
            return toDetail(currentUser, target);
        }

        target.setActive(true);
        userRepository.save(target);
        notificationService.safeCreateNotification(
                target,
                NotificationType.SYSTEM,
                "Your account is active again",
                "An admin reactivated your account. Welcome back.",
                AuditLogQueryService.TARGET_USER,
                target.getId(),
                null,
                admin);
        auditLogService.record(admin, ACTION_REACTIVATE,
                new AuditTarget(AuditLogQueryService.TARGET_USER, target.getId(), target.getDisplayName()), reason,
                "Admin reactivated the account; it can sign in again.");
        return toDetail(currentUser, target);
    }

    private AdminUserListItemResponse toListItem(User user, TeacherProfile profile) {
        return new AdminUserListItemResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                AccountRoles.primary(user),
                user.isActive(),
                profile == null ? null : TeacherVerificationState.of(profile),
                user.getCreatedAt());
    }

    private AdminUserDetailResponse toDetail(SecurityUserDetails currentUser, User user) {
        TeacherProfile profile = teacherProfileRepository.findByUserId(user.getId()).orElse(null);
        boolean self = user.getId().equals(currentUser.getUserId());
        AdminUserDetailResponse.StatusChange deactivation = null;
        if (!user.isActive()) {
            deactivation = auditLogRepository
                    .findFirstByTargetTypeAndTargetIdAndActionTypeOrderByCreatedAtDescIdDesc(
                            AuditLogQueryService.TARGET_USER, user.getId(), ACTION_DEACTIVATE)
                    .map(AdminUserService::toStatusChange)
                    .orElse(null);
        }
        AdminUserDetailResponse.DeactivationImpact impact = null;
        if (user.isActive() && !self) {
            DeactivationTally preview = new DeactivationTally();
            deactivationSteps.forEach(step -> step.preview(user, preview));
            impact = preview.toImpact();
        }
        TeacherVerificationState state = profile == null ? null : TeacherVerificationState.of(profile);
        return new AdminUserDetailResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                AccountRoles.primary(user),
                user.isActive(),
                state,
                state == TeacherVerificationState.APPROVED ? profile.getVerifiedAt() : null,
                user.getCreatedAt(),
                deactivation,
                self,
                impact,
                auditLogQueryService.recentForUser(user, profile == null ? null : profile.getId()));
    }

    private static AdminUserDetailResponse.StatusChange toStatusChange(AuditLog entry) {
        User actor = entry.getActorUser();
        return new AdminUserDetailResponse.StatusChange(
                actor == null ? null : actor.getDisplayName(),
                entry.getActorEmail(),
                entry.getReason(),
                entry.getCreatedAt());
    }

    private User requireUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User was not found."));
    }

    private static RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return RoleName.tryFrom(role)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid user role filter."));
    }
}
