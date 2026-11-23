package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminUserDetailResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * The admin Users page: search (learners and admins), one account in detail with its own recent events, deactivating an
 * account with a reason (it is signed out at once) and reactivating it (no reason needed).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ACTION_DEACTIVATE = "ADMIN_USER_DEACTIVATE";
    private static final String ACTION_REACTIVATE = "ADMIN_USER_REACTIVATE";

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogQueryService auditLogQueryService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final CurrentAccount currentAccount;

    /** {@code keyword} matches the name or e-mail; {@code role} (STUDENT or ADMIN) and {@code active} narrow the list. */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> list(String keyword, String role, Boolean active, int page, int size) {
        RoleName roleName = parseRole(role);
        String search = TextUtils.lowerCaseOrNull(keyword);
        Page<Long> idPage = PaginationUtils.readPage(PaginationUtils.newestFirst(page, size),
                request -> userRepository.findAdminUserIds(roleName, search, active, request));
        List<Long> ids = idPage.getContent();
        Map<Long, User> users = ids.isEmpty() ? Map.of() : userRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.from(idPage, id -> toListItem(users.get(id)));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(SecurityUserDetails currentUser, Long userId) {
        return toDetail(currentUser, requireUser(userId));
    }

    /** Signs the account out now and blocks sign-in; the reason is audited. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserDetailResponse deactivate(SecurityUserDetails currentUser, Long userId, String reason) {
        User target = requireUser(userId);
        User admin = currentAccount.lockWith(currentUser, target);
        if (admin.getId().equals(target.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot deactivate your own admin account.");
        }
        if (!target.isActive()) {
            return toDetail(currentUser, target);
        }

        target.setActive(false);
        refreshTokenService.invalidateSessions(target);
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.retireOpenTokensForUser(target.getId(), now);
        emailChangeTokenRepository.retireOpenTokensForUser(target.getId(), now);
        auditLogService.record(admin, ACTION_DEACTIVATE,
                new AuditTarget(AuditLogQueryService.TARGET_USER, target.getId(), target.getDisplayName()), reason,
                "Admin deactivated the account: signed out, sign-in blocked.");
        return toDetail(currentUser, target);
    }

    /**
     * Lets the account sign in again. It runs at once and needs no reason, since it is easy to take back: {@code reason}
     * is null then, and the audit entry has none.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserDetailResponse reactivate(SecurityUserDetails currentUser, Long userId, String reason) {
        User target = requireUser(userId);
        User admin = currentAccount.lockWith(currentUser, target);
        if (target.getDeletedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "A deleted account can't be reactivated.");
        }
        if (target.getRoles().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "This account has no role any more, so it can't be reactivated.");
        }
        if (target.isActive()) {
            return toDetail(currentUser, target);
        }

        target.setActive(true);
        notificationService.safeCreateNotification(
                target,
                NotificationType.SYSTEM,
                "Your account is active again",
                "An admin reactivated your account. Welcome back.",
                AuditLogQueryService.TARGET_USER,
                target.getId(),
                null);
        auditLogService.record(admin, ACTION_REACTIVATE,
                new AuditTarget(AuditLogQueryService.TARGET_USER, target.getId(), target.getDisplayName()), reason,
                "Admin reactivated the account; it can sign in again.");
        return toDetail(currentUser, target);
    }

    private static AdminUserListItemResponse toListItem(User user) {
        return new AdminUserListItemResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                role(user),
                user.isActive(),
                user.getCreatedAt());
    }

    private AdminUserDetailResponse toDetail(SecurityUserDetails currentUser, User user) {
        boolean self = user.getId().equals(currentUser.getUserId());
        AdminUserDetailResponse.StatusChange deactivation = (user.isActive() || user.getDeletedAt() != null) ? null
                : auditLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionTypeOrderByCreatedAtDescIdDesc(
                        AuditLogQueryService.TARGET_USER, user.getId(), ACTION_DEACTIVATE)
                .map(AdminUserService::toStatusChange)
                .orElse(null);
        return new AdminUserDetailResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                role(user),
                user.isActive(),
                user.getCreatedAt(),
                deactivation,
                self,
                auditLogQueryService.recentForAccount(user.getId()));
    }

    /** The account's role; null for an account left without one (a teacher account, deactivated when teachers went). */
    private static RoleName role(User user) {
        return user.orderedRoleNames().findFirst().orElse(null);
    }

    private static AdminUserDetailResponse.StatusChange toStatusChange(AuditLog entry) {
        User actor = entry.getActorUser();
        return new AdminUserDetailResponse.StatusChange(
                actor == null ? null : actor.getDisplayName(),
                entry.getReason(),
                entry.getCreatedAt());
    }

    private User requireUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User was not found."));
    }

    /** The role filter (STUDENT or ADMIN); null for none. */
    private static RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return RoleName.tryFrom(role)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid user role filter."));
    }
}
