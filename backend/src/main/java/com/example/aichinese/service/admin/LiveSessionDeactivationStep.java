package com.example.aichinese.service.admin;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.service.NotificationService;

import jakarta.persistence.EntityManager;

/**
 * A deactivated teacher's upcoming live sessions are cancelled: their bookings end and the booked students are told.
 * Sessions that already started or ended keep their records.
 */
@Component
class LiveSessionDeactivationStep implements AccountDeactivationStep {

    private static final String STUDENT_LIVE_SESSIONS_PATH = "/student/classes/live";

    private final EntityManager entityManager;
    private final NotificationService notificationService;

    LiveSessionDeactivationStep(EntityManager entityManager, NotificationService notificationService) {
        this.entityManager = entityManager;
        this.notificationService = notificationService;
    }

    @Override
    public void preview(User user, DeactivationTally impact) {
        List<LiveClass> sessions = upcomingSessions(user);
        impact.addUpcomingSessions(sessions.size());
        impact.addBookings(bookings(sessions).size());
    }

    @Override
    public void apply(User user, User admin, DeactivationTally done) {
        List<LiveClass> sessions = upcomingSessions(user);
        List<LiveClassEnrolment> bookings = bookings(sessions);
        LocalDateTime now = LocalDateTime.now();
        sessions.forEach(session -> session.setLiveClassStatus(LiveClassStatus.CANCELLED));
        for (LiveClassEnrolment booking : bookings) {
            booking.setEnrolmentStatus(LiveClassEnrolmentStatus.CANCELLED);
            booking.setCancelledAt(now);
            User student = booking.getStudent().getUser();
            if (student != null) {
                notificationService.safeCreateNotification(
                        student,
                        NotificationType.LIVE_CLASS_REMINDER,
                        "Live session cancelled",
                        "“" + booking.getLiveClass().getTitle() + "” was cancelled and your booking ended.",
                        "LIVE_CLASS",
                        booking.getLiveClass().getId(),
                        STUDENT_LIVE_SESSIONS_PATH,
                        admin);
            }
        }
        done.addUpcomingSessions(sessions.size());
        done.addBookings(bookings.size());
    }

    private List<LiveClass> upcomingSessions(User user) {
        return entityManager.createQuery("""
                        select lc from LiveClass lc
                        where lc.teacher.user.id = :userId
                          and lc.scheduledStartAt > :now
                          and lc.liveClassStatus not in :closed
                        """, LiveClass.class)
                .setParameter("userId", user.getId())
                .setParameter("now", LocalDateTime.now())
                .setParameter("closed", LiveClassStatus.TERMINAL)
                .getResultList();
    }

    private List<LiveClassEnrolment> bookings(List<LiveClass> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select e from LiveClassEnrolment e
                        join fetch e.student s
                        left join fetch s.user
                        where e.liveClass in :sessions and e.enrolmentStatus = :enrolled
                        """, LiveClassEnrolment.class)
                .setParameter("sessions", sessions)
                .setParameter("enrolled", LiveClassEnrolmentStatus.ENROLLED)
                .getResultList();
    }
}
