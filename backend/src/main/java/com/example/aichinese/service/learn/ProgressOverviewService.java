package com.example.aichinese.service.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/** The student's Progress page: XP level, streak calendar and badges. */
@Service
@RequiredArgsConstructor
public class ProgressOverviewService {

    private final XpService xpService;
    private final StudentBadgeRepository studentBadgeRepository;
    private final LearnClock clock;
    private final StudentLearningAccessService accessService;

    @Transactional(readOnly = true)
    public ProgressOverviewResponse overview(SecurityUserDetails currentUser, YearMonth month) {
        return overview(accessService.requireStudent(currentUser), month);
    }

    /** @param month the calendar month to show; null (or outside the student's months) = this month */
    @Transactional(readOnly = true)
    public ProgressOverviewResponse overview(StudentProfile student, YearMonth month) {
        LocalDate today = clock.today();
        LocalDate joinedOn = clock.dayOf(student.getCreatedAt());
        YearMonth first = YearMonth.from(joinedOn);
        YearMonth current = YearMonth.from(today);
        YearMonth shown = month == null || month.isAfter(current) || month.isBefore(first) ? current : month;

        int totalXp = student.getTotalPoints();
        int level = RewardRules.levelFor(totalXp);
        XpService.StreakState streak = xpService.streak(student, today);
        List<LocalDate> goalDays = xpService.goalDays(student, shown.atDay(1), shown.atEndOfMonth()).stream()
                .sorted()
                .toList();
        return new ProgressOverviewResponse(
                totalXp,
                level,
                RewardRules.xpIntoLevel(totalXp),
                level * RewardRules.XP_PER_LEVEL,
                streak.current(),
                streak.best(),
                new ProgressOverviewResponse.Calendar(
                        shown.toString(),
                        today,
                        joinedOn,
                        goalDays,
                        shown.isAfter(first) ? shown.minusMonths(1).toString() : null,
                        shown.isBefore(current) ? shown.plusMonths(1).toString() : null),
                badges(student));
    }

    private List<ProgressOverviewResponse.BadgeView> badges(StudentProfile student) {
        Map<String, LocalDateTime> earned = studentBadgeRepository.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(badge -> badge.getBadge().getName(), StudentBadge::getAwardedAt));
        return Arrays.stream(RewardBadge.values())
                .map(badge -> new ProgressOverviewResponse.BadgeView(
                        badge.name(), earned.containsKey(badge.badgeName()), earned.get(badge.badgeName())))
                .toList();
    }
}
