package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.entity.enums.RoleName;

/** The user drawer of the admin Users page. */
public record AdminUserDetailResponse(
        Long id,
        String fullName,
        String email,
        RoleName role,
        boolean active,
        LocalDateTime joinedAt,
        /** The latest deactivation while the account is deactivated: who, when and why. */
        StatusChange deactivation,
        /** True when this is the signed-in admin's own account (it cannot be deactivated from here). */
        boolean self,
        /** The account's own recent events ("Account created", "Account deactivated", ...), newest first. */
        List<AuditEntryResponse> recentAudit) {

    public record StatusChange(String actorName, String reason, LocalDateTime at) {

        @Override
        public String toString() {
            return "StatusChange[actorName=" + redactValue(actorName)
                    + ", reason=" + redactLength(reason)
                    + ", at=" + at + "]";
        }
    }

    @Override
    public String toString() {
        return "AdminUserDetailResponse[id=" + id
                + ", fullName=" + redactValue(fullName)
                + ", email=" + redactValue(email)
                + ", role=" + role
                + ", active=" + active
                + ", joinedAt=" + joinedAt
                + ", deactivation=" + deactivation
                + ", self=" + self
                + ", recentAudit=" + (recentAudit == null ? 0 : recentAudit.size()) + "]";
    }
}
