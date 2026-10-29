package com.example.aichinese.service.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;

/** The student's Progress page: XP level, streak calendar, where the XP comes from, recent XP and badges. */
@Service
public class ProgressOverviewService {

    private static final int RECENT_LIMIT = 6;

    private final XpService xpService;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final LearnClock clock;

    public ProgressOverviewService(
            XpService xpService,
            PointsTransactionRepository pointsTransactionRepository,
            StudentBadgeRepository studentBadgeRepository,
            LearnClock clock) {
        this.xpService = xpService;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.clock = clock;
    }

    /** @param month the calendar month to show; null (or outside the student's months) = this month */
    @Transactional(readOnly = true)
    public ProgressOverviewResponse overview(StudentProfile student, YearMonth month) {
        LocalDate today = clock.today();
        LocalDate joinedOn = student.getCreatedAt() == null ? today : clock.dayOf(student.getCreatedAt());
        YearMonth first = YearMonth.from(joinedOn);
        YearMonth current = YearMonth.from(today);
        YearMonth shown = month == null || month.isAfter(current) || month.isBefore(first) ? current : month;

        int totalXp = student.getTotalPoints() == null ? 0 : student.getTotalPoints();
        XpService.StreakState streak = xpService.streak(student);
        List<LocalDate> goalDays = xpService.goalDays(student, shown.atDay(1), shown.atEndOfMonth()).stream()
                .sorted()
                .toList();
        return new ProgressOverviewResponse(
                totalXp,
                RewardRules.levelFor(totalXp),
                RewardRules.progressToNextLevelPercent(totalXp),
                RewardRules.levelFor(totalXp) * RewardRules.XP_PER_LEVEL,
                xpService.dailyGoalOf(student).dailyGoalXp(),
                xpService.todayXp(student),
                xpService.goalReachedOn(student, today),
                streak.current(),
                streak.best(),
                new ProgressOverviewResponse.Calendar(
                        shown.toString(),
                        today,
                        joinedOn,
                        goalDays,
                        shown.isAfter(first) ? shown.minusMonths(1).toString() : null,
                        shown.isBefore(current) ? shown.plusMonths(1).toString() : null),
                sources(student),
                pointsTransactionRepository.findByStudentIdOrderByCreatedAtDesc(student.getId(), PageRequest.of(0, RECENT_LIMIT))
                        .getContent().stream()
                        .map(ProgressOverviewService::toRecent)
                        .toList(),
                badges(student));
    }

    private ProgressOverviewResponse.Sources sources(StudentProfile student) {
        int learn = 0;
        int practice = 0;
        int reviews = 0;
        int earlier = 0;
        for (Object[] row : pointsTransactionRepository.sumBySourceType(student.getId())) {
            String source = (String) row[0];
            int xp = ((Number) row[1]).intValue();
            switch (kindOf(source)) {
                case "LEARN_LESSON" -> learn += xp;
                case "PRACTICE_SET", "QUIZ_STREAK" -> practice += xp;
                case "MISTAKES_REVIEW", "DAILY_GOAL" -> reviews += xp;
                default -> earlier += xp;
            }
        }
        return new ProgressOverviewResponse.Sources(learn, practice, reviews, earlier);
    }

    private List<ProgressOverviewResponse.BadgeView> badges(StudentProfile student) {
        Map<String, LocalDateTime> earned = studentBadgeRepository.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(badge -> badge.getBadge().getName(), StudentBadge::getAwardedAt,
                        (first, second) -> first));
        return Arrays.stream(RewardBadge.values())
                .map(badge -> new ProgressOverviewResponse.BadgeView(
                        badge.name(), earned.containsKey(badge.badgeName()), earned.get(badge.badgeName())))
                .toList();
    }

    private static ProgressOverviewResponse.RecentXp toRecent(PointsTransaction transaction) {
        String kind = kindOf(transaction.getSourceType());
        return new ProgressOverviewResponse.RecentXp(
                kind, transaction.getReason(), transaction.getPointsDelta(), transaction.getCreatedAt());
    }

    /** The XP source kind of a points row; rows from before the current rules are EARLIER. */
    static String kindOf(String sourceType) {
        if (sourceType == null) {
            return "EARLIER";
        }
        if (sourceType.startsWith(XpService.SOURCE_PRACTICE_PREFIX)) {
            return "PRACTICE_SET";
        }
        return switch (sourceType) {
            case XpService.SOURCE_LEARN_LESSON, XpService.SOURCE_QUIZ_STREAK, XpService.SOURCE_MISTAKES_REVIEW,
                    XpService.SOURCE_DAILY_GOAL -> sourceType;
            default -> "EARLIER";
        };
    }
}
