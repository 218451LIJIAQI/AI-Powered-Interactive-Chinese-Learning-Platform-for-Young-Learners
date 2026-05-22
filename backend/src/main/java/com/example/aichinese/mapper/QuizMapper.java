package com.example.aichinese.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.learning.QuizOptionResponse;
import com.example.aichinese.dto.learning.StudentQuizQuestionResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.StudentQuizSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizOptionResponse;
import com.example.aichinese.dto.learning.TeacherQuizQuestionResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;

/**
 * Entity to DTO mapping for quiz objects (teacher view + student view).
 */
@Component
public class QuizMapper {

    public TeacherQuizResponse toTeacherQuizResponse(
            Quiz quiz,
            List<QuizQuestion> questions,
            Map<Long, List<QuizOption>> optionsByQuestionId) {
        return new TeacherQuizResponse(
                quiz.getId(),
                quiz.getLesson() != null ? quiz.getLesson().getId() : null,
                quiz.getLesson() != null ? quiz.getLesson().getTitle() : null,
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getContentStatus(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questions.stream()
                        .map(question -> toTeacherQuizQuestionResponse(
                                question,
                                optionsByQuestionId.getOrDefault(question.getId(), List.of())))
                        .toList(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt());
    }

    public TeacherQuizQuestionResponse toTeacherQuizQuestionResponse(QuizQuestion question, List<QuizOption> options) {
        return new TeacherQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getCorrectAnswerText(),
                question.getPoints(),
                question.getSortOrder(),
                question.getExplanation(),
                options.stream()
                        .map(option -> new TeacherQuizOptionResponse(
                                option.getId(),
                                option.getOptionText(),
                                option.isCorrectOption(),
                                option.getSortOrder()))
                        .toList());
    }

    public StudentQuizResponse toStudentQuizResponse(
            Quiz quiz,
            List<QuizQuestion> questions,
            Map<Long, List<QuizOption>> optionsByQuestionId) {
        return new StudentQuizResponse(
                quiz.getId(),
                quiz.getLesson() != null ? quiz.getLesson().getId() : null,
                quiz.getLesson() != null ? quiz.getLesson().getTitle() : null,
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questions.stream()
                        .map(question -> toStudentQuizQuestionResponse(
                                question,
                                optionsByQuestionId.getOrDefault(question.getId(), List.of())))
                        .toList());
    }

    public StudentQuizQuestionResponse toStudentQuizQuestionResponse(QuizQuestion question, List<QuizOption> options) {
        return new StudentQuizQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getPoints(),
                question.getSortOrder(),
                options.stream()
                        .map(option -> new QuizOptionResponse(
                                option.getId(),
                                option.getOptionText(),
                                option.getSortOrder()))
                        .toList());
    }

    public StudentQuizSummaryResponse toStudentQuizSummaryResponse(Quiz quiz, int questionCount) {
        return new StudentQuizSummaryResponse(
                quiz.getId(),
                quiz.getLesson() != null ? quiz.getLesson().getId() : null,
                quiz.getLesson() != null ? quiz.getLesson().getTitle() : null,
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getTimeLimitMinutes(),
                quiz.getTotalPoints(),
                questionCount);
    }

    public Map<Long, List<QuizOption>> groupOptionsByQuestionId(List<QuizOption> options) {
        return options.stream().collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
    }
}
