package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.enums.RoleName;

/** The user drawer of the admin Users page. */
public record AdminUserDetailResponse(
        Long id,
        String fullName,
        String email,
        RoleName role,
        boolean active,
        TeacherVerificationState verificationState,
        LocalDateTime approvedAt,
        LocalDateTime joinedAt,
        /** The latest deactivation while the account is deactivated: who, when and why. */
        StatusChange deactivation,
        /** True when this is the signed-in admin's own account (it cannot be deactivated from here). */
        boolean self,
        /** What deactivating the account would do now; null when it is already deactivated or is the admin's own. */
        DeactivationImpact deactivationImpact,
        /** Actions by or about this account, newest first. */
        List<AuditEntryResponse> recentAudit) {

    public AdminUserDetailResponse {
        recentAudit = DtoNormalization.immutableListOrEmpty(recentAudit);
    }

    public record StatusChange(String actorName, String actorEmail, String reason, LocalDateTime at) {
    }

    /** The consequences listed in the Deactivate dialog. */
    public record DeactivationImpact(
            List<String> publishedClasses,
            long upcomingSessions,
            long bookings,
            long reviewItems) {

        public DeactivationImpact {
            publishedClasses = DtoNormalization.immutableListOrEmpty(publishedClasses);
        }
    }
}
