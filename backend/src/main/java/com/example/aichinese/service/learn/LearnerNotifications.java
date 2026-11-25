package com.example.aichinese.service.learn;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * The learners' in-app notifications (S3.09): a rank reached, a new Practice set, an updated class lesson, the evening
 * daily-goal reminder and the weekly mistakes reminder ({@link LearnerReminderJobs} sends the last two). Each is written
 * after the caller's transaction commits and never fails it ({@link NotificationService#safeCreateNotification}); the
 * texts are stored in English and translated by the notifications catalogue (serverMessageRows/notifications.ts).
 */
@Component
@RequiredArgsConstructor
public class LearnerNotifications {

    /** "New in Practice" goes to the learners who earned XP in this many days, today included. */
    private static final int ACTIVE_DAYS = 30;
    private static final String RANK_TARGET = "RANK";
    private static final String LESSON_TARGET = "LESSON";
    private static final String PROGRESS_PATH = "/student/progress";
    private static final String HOME_PATH = "/student/home";
    private static final String MISTAKES_PATH = "/student/practice/mistakes";

    private final NotificationService notificationService;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final LearnClock clock;

    /** "You reached Rank 3": the learner's XP passed the threshold of {@code rank}. Opens Progress. */
    public static Notification rankReachedDraft(int rank) {
        return NotificationService.draft(NotificationType.RANK_REACHED,
                "You reached Rank " + rank,
                "You're now Rank " + rank + " · " + rankTitle(rank) + ". Rank " + (rank + 1) + " is next.",
                RANK_TARGET, (long) rank, PROGRESS_PATH);
    }

    public static Notification newPracticeSetDraft(PracticeType type, Long setId, String setTitle) {
        String path = "/student/practice/" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "?set=" + setId;
        String message = "A new " + typeName(type) + " set is ready: “" + setTitle + "”.";
        return NotificationService.draft(NotificationType.NEW_PRACTICE_SET, "New in Practice", message,
                type.referenceType(), setId, path);
    }

    public static Notification lessonUpdatedDraft(Long classId, String classTitle, Long lessonId, String lessonTitle) {
        String path = "/student/classes/" + classId + "/lessons/" + lessonId;
        String message = "“" + lessonTitle + "” in " + classTitle + " was updated with new content.";
        return NotificationService.draft(NotificationType.LESSON_UPDATED, "Lesson updated", message,
                LESSON_TARGET, lessonId, path);
    }

    public static Notification dailyGoalReminderDraft(long xpToGo) {
        return NotificationService.draft(NotificationType.DAILY_GOAL_REMINDER,
                "Daily goal reminder",
                "You're " + xpToGo + " XP away from today's goal.",
                null, null, HOME_PATH);
    }

    public static Notification mistakesToReviewDraft(long waiting) {
        long minutes = Math.ceilDiv(waiting, 2);
        return NotificationService.draft(NotificationType.MISTAKES_TO_REVIEW,
                "Mistakes to review",
                "You have " + waiting + " mistakes saved. Fixing them takes about " + minutes + " minutes.",
                null, null, MISTAKES_PATH);
    }

    /** "You reached Rank 3": the learner's XP passed the threshold of {@code rank}. Opens Progress. */
    public void rankReached(User learner, int rank) {
        notificationService.safeCreateNotification(learner, rankReachedDraft(rank));
    }

    /**
     * "New in Practice", once per set (publishing it again after an unpublish says nothing new): to the learners who
     * earned XP in the last {@value #ACTIVE_DAYS} days, today included. Opens the set on its Practice tab.
     */
    public void newPracticeSet(PracticeType type, PracticeActivityContent set) {
        Long setId = set.getId();
        String setTitle = set.getTitle();
        var activeSince = clock.startOf(clock.today().minusDays(ACTIVE_DAYS - 1L));
        notificationService.safeCreateNotifications(
                (afterId, slice) -> pointsTransactionRepository.findActiveLearnerIdsSinceAfter(activeSince, afterId, slice),
                () -> newPracticeSetDraft(type, setId, setTitle),
                type.referenceType() + ":" + setId);
    }

    /** "Lesson updated": an admin published changes to a lesson; the learners who joined its class are told. */
    public void lessonUpdated(Long classId, String classTitle, Long lessonId, String lessonTitle) {
        notificationService.safeCreateNotifications(
                (afterId, slice) -> enrolmentRepository.findActiveLearnerIdsOfClassAfter(classId, afterId, slice),
                () -> lessonUpdatedDraft(classId, classTitle, lessonId, lessonTitle),
                LESSON_TARGET + ":" + lessonId);
    }

    /** The evening reminders: {@code xpToGo} XP still missing per learner id. Dispatched in chunked batches. */
    public void dailyGoalReminders(Map<Long, Long> xpToGoByLearnerId) {
        Map<Long, Notification> reminders = new LinkedHashMap<>(xpToGoByLearnerId.size());
        xpToGoByLearnerId.forEach((learnerId, xpToGo) -> reminders.put(learnerId, dailyGoalReminderDraft(xpToGo)));
        notificationService.safeCreatePersonalizedNotifications(reminders, NotificationType.DAILY_GOAL_REMINDER);
    }

    /** The weekly reminders of waiting mistakes per learner id, about half a minute each. Dispatched in chunked batches. */
    public void mistakesToReviewReminders(Map<Long, Long> waitingByLearnerId) {
        Map<Long, Notification> reminders = new LinkedHashMap<>(waitingByLearnerId.size());
        waitingByLearnerId.forEach((learnerId, waiting) -> reminders.put(learnerId, mistakesToReviewDraft(waiting)));
        notificationService.safeCreatePersonalizedNotifications(reminders, NotificationType.MISTAKES_TO_REVIEW);
    }

    /** The rank titles of Progress and the sidebar ("Rank 3 · Regular"); the champion title starts at Rank 5. */
    private static String rankTitle(int rank) {
        if (rank <= 1) {
            return "Starter";
        }
        if (rank == 2) {
            return "Explorer";
        }
        if (rank == 3) {
            return "Regular";
        }
        if (rank == 4) {
            return "Committed";
        }
        return "Spark Champion";
    }

    /** The activity as the notification names it ("A new listening set is ready"). */
    private static String typeName(PracticeType type) {
        return switch (type) {
            case FLASHCARDS -> "flashcards";
            case LISTENING -> "listening";
            case TRACING -> "tracing";
            case DRAG_DROP -> "drag & drop";
            case MATCHING -> "matching";
            case TYPING -> "typing";
        };
    }
}
