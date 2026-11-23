package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;

import jakarta.persistence.LockModeType;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    Optional<CurrentUserResponse.StudentProfileSummary> findSummaryByUser(User user);

    /** A demo persona's profile (addresses are stored in lower case). */
    Optional<StudentProfile> findByUserEmail(String email);

    /**
     * The profile row locked for update, without a join: a student's XP awards, class lesson steps, hint requests and
     * plan changes are serialised on it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentProfile> findLockedById(Long id);

    record DailyGoal(Long learnerId, long remainingXp) {
    }

    /** Active learners with a plan who still have XP left for the platform day, excluding goals already awarded. */
    @Query("""
            select new com.example.aichinese.repository.StudentProfileRepository$DailyGoal(
                u.id, s.dailyGoalXp - sum(case when t.createdAt >= :dayStart then t.pointsDelta else 0 end))
            from StudentProfile s join s.user u
            join PointsTransaction t on t.student = s and t.createdAt >= :activeSince and t.createdAt < :dayEnd
            where u.active = true and u.deletedAt is null
              and exists (select 1 from LearningPlan p where p.student = s and p.active = true)
              and not exists (select 1 from PointsTransaction g
                              where g.student = s and g.sourceType = :goalSource and g.sourceId = :epochDay)
            group by u.id, s.dailyGoalXp
            having s.dailyGoalXp > sum(case when t.createdAt >= :dayStart then t.pointsDelta else 0 end)
            """)
    List<DailyGoal> findDailyGoalsForReminder(
            @Param("activeSince") LocalDateTime activeSince,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("goalSource") String goalSource,
            @Param("epochDay") Long epochDay);
}
