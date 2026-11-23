package com.example.aichinese.service.learn;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.AnswerInsightsResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.PracticeAnswerRepository;
import com.example.aichinese.repository.PracticeAnswerRepository.InsightCounts;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/** How sure the student's Practice answers were over the last 7 days, and how many came after a hint. */
@Service
@RequiredArgsConstructor
public class AnswerInsightsService {

    private final PracticeAnswerRepository answerRepository;
    private final LearnClock clock;
    private final StudentLearningAccessService accessService;

    @Transactional(readOnly = true)
    public AnswerInsightsResponse thisWeek(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        List<InsightCounts> counts = answerRepository.countInsightsSince(student.getId(), clock.weekStart());
        long sureAndRight = 0;
        long luckyGuesses = 0;
        long sureButWrong = 0;
        long wrongGuesses = 0;
        long hintsUsed = 0;
        for (InsightCounts row : counts) {
            sureAndRight += row.getSureAndRight();
            luckyGuesses += row.getLuckyGuesses();
            sureButWrong += row.getSureButWrong();
            wrongGuesses += row.getWrongGuesses();
            hintsUsed += row.getHintsUsed();
        }
        PracticeType mostHintsIn = hintsUsed == 0 ? null : counts.getFirst().getPracticeType();
        return new AnswerInsightsResponse(sureAndRight, luckyGuesses, sureButWrong, wrongGuesses, hintsUsed, mostHintsIn);
    }
}
