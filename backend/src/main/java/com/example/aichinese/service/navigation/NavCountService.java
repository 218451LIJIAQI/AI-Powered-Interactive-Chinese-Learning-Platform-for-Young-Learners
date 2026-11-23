package com.example.aichinese.service.navigation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.account.NavCountsResponse;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.support.SupportService;

import lombok.RequiredArgsConstructor;

/**
 * Sidebar badge counts of the signed-in user, per the frontend's {@code RoleNavItem.countKey}. Admins see what waits for
 * them (AI reports to review, open support requests); learners their mistakes waiting for review.
 */
@Service
@RequiredArgsConstructor
public class NavCountService {

    private final LearningMistakeRepository mistakeRepository;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final SupportService supportService;

    @Transactional(readOnly = true)
    public NavCountsResponse countsFor(SecurityUserDetails currentUser) {
        Long aiReports = null;
        Long supportOpen = null;
        Long mistakes = null;
        if (currentUser.hasRole(RoleName.ADMIN)) {
            AdminQueueCounts queues = adminQueueCounts();
            aiReports = queues.aiReports();
            supportOpen = queues.supportOpen();
        }
        if (currentUser.hasRole(RoleName.STUDENT)) {
            mistakes = mistakeRepository.countByStudentUserIdAndClearedAtIsNull(currentUser.getUserId());
        }
        return new NavCountsResponse(mistakes, aiReports, supportOpen);
    }

    /**
     * The queues of the admin badges: AI reports waiting for review (AI) and support requests waiting for support
     * (Support). The admins' daily summary e-mail reports these same numbers.
     */
    @Transactional(readOnly = true)
    public AdminQueueCounts adminQueueCounts() {
        return new AdminQueueCounts(
                aiResponseReportRepository.countByStatus(AiResponseReportStatus.PENDING_REVIEW),
                supportService.countOpen());
    }

    public record AdminQueueCounts(long aiReports, long supportOpen) {
    }
}
