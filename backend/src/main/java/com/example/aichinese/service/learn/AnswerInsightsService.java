package com.example.aichinese.service.learn;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.AnswerInsightsResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.PracticeAnswerRepository;
import com.example.aichinese.repository.PracticeAnswerRepository.InsightCounts;
import com.example.aichinese.repository.ServedHintRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/** How sure the student's Practice answers were over the last 7 days, and how many Practice hints were used. */
@Service
@RequiredArgsConstructor
public class AnswerInsightsService {

    private final PracticeAnswerRepository answerRepository;
    private final ServedHintRepository servedHintRepository;
    private final LearnClock clock;
    private final StudentLearningAccessService accessService;

    @Transactional(readOnly = true)
    public AnswerInsightsResponse thisWeek(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        LocalDateTime weekStart = clock.weekStart();
        List<InsightCounts> counts = answerRepository.countInsightsSince(student.getId(), weekStart);
        long sureAndRight = 0;
        long luckyGuesses = 0;
        long sureButWrong = 0;
        long wrongGuesses = 0;
        long hintsUsed = 0;
        PracticeType mostHintsIn = null;
        long topHintCount = 0;
        for (InsightCounts row : counts) {
            sureAndRight += row.getSureAndRight();
            luckyGuesses += row.getLuckyGuesses();
            sureButWrong += row.getSureButWrong();
            wrongGuesses += row.getWrongGuesses();
            long typeHints = row.getHintsUsed();
            hintsUsed += typeHints;
            if (isHigherHintLeader(row.getPracticeType(), typeHints, mostHintsIn, topHintCount)) {
                mostHintsIn = row.getPracticeType();
                topHintCount = typeHints;
            }
        }
        long tracingHints = servedHintRepository.countTracingHintsSince(student.getId(), weekStart);
        if (tracingHints > 0) {
            hintsUsed += tracingHints;
            if (isHigherHintLeader(PracticeType.TRACING, tracingHints, mostHintsIn, topHintCount)) {
                mostHintsIn = PracticeType.TRACING;
            }
        }
        return new AnswerInsightsResponse(sureAndRight, luckyGuesses, sureButWrong, wrongGuesses, hintsUsed, mostHintsIn);
    }

    private static boolean isHigherHintLeader(
            PracticeType candidate, long candidateHints, PracticeType currentLeader, long leaderHints) {
        if (candidateHints <= 0) {
            return false;
        }
        if (currentLeader == null || candidateHints > leaderHints) {
            return true;
        }
        return candidateHints == leaderHints && candidate.name().compareTo(currentLeader.name()) < 0;
    }
}
