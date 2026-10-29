package com.example.aichinese.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.XpService;

/**
 * The reward summary the sidebar shows: total XP and the current streak. Classes earn no XP and do not count towards
 * the daily goal or the streak (only Learn, Practice, the Mistakes review and the daily goal do, see {@link XpService}).
 */
@Service
public class StudentLearningProgressService {

    private final StudentLearningAccessService studentLearningAccessService;
    private final XpService xpService;

    public StudentLearningProgressService(StudentLearningAccessService studentLearningAccessService, XpService xpService) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.xpService = xpService;
    }

    @Transactional(readOnly = true)
    public RewardSummaryResponse getRewards(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int totalPoints = student.getTotalPoints() == null ? 0 : student.getTotalPoints();
        return new RewardSummaryResponse(totalPoints, xpService.streak(student).current());
    }
}
