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
 * Entity-to-DTO translation for quiz objects.
 *
 * <p>The mapper emits two distinct response shapes from the same
 * underlying entity graph:</p>
 * <ul>
 *   <li><b>Teacher view</b> ({@link TeacherQuizResponse},
 *       {@link TeacherQuizQuestionResponse},
 *       {@link TeacherQuizOptionResponse}) includes the correct-answer
 *       text, the per-option correctness flag and the explanation — all
 *       fields a teacher needs to author or grade questions.</li>
 *   <li><b>Student view</b> ({@link StudentQuizResponse},
 *       {@link StudentQuizQuestionResponse},
 *       {@link QuizOptionResponse}) deliberately omits every
 *       correctness-leak field, so a student cannot inspect the
 *       network response to skip the assessment. This is a security
 *       boundary: the student-facing service path must always invoke
 *       the {@code toStudent*} methods on this class, never the
 *       teacher-facing ones.</li>
 * </ul>
 *
 * <p>Options are joined to questions via a pre-computed
 * {@code Map<questionId, List<QuizOption>>} (see
 * {@link #groupOptionsByQuestionId(List)}) so a single batched fetch
 * feeds N questions instead of triggering one option SELECT per
 * question.</p>
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
